package com.techenglish.vector;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.sqlite.SQLiteConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Optional sqlite-vec index. When the extension cannot be loaded the app keeps
 * working: VectorSearchService falls back to cosine similarity in Java.
 */
@Component
@RequiredArgsConstructor
public class SqliteVecIndex {

    private static final Logger log = LoggerFactory.getLogger(SqliteVecIndex.class);
    private static final String TABLE = "embedding_vec";

    private final Environment environment;
    private final VectorProperties properties;
    private final ObjectMapper objectMapper;

    private final Object lock = new Object();
    private volatile boolean initialized;
    private volatile boolean available;
    private Connection connection;

    public record Match(long documentId, double distance) {
    }

    public boolean available() {
        ensureInitialized();
        return available;
    }

    public boolean rebuild(List<EmbeddingDocument> documents) {
        synchronized (lock) {
            if (!ensureInitialized()) {
                return false;
            }
            try {
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("DELETE FROM " + TABLE);
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO " + TABLE + " (rowid, embedding) VALUES (?, ?)")) {
                    for (EmbeddingDocument document : documents) {
                        insert.setLong(1, document.getId());
                        insert.setString(2, document.getVectorJson());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                connection.commit();
                log.info("sqlite-vec index rebuilt with {} document(s).", documents.size());
                return true;
            } catch (SQLException ex) {
                rollbackQuietly();
                log.warn("sqlite-vec rebuild failed, using Java cosine fallback: {}", ex.getMessage());
                disable();
                return false;
            } finally {
                restoreAutoCommit();
            }
        }
    }

    public void upsert(EmbeddingDocument document) {
        synchronized (lock) {
            if (!ensureInitialized()) {
                return;
            }
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + TABLE + " WHERE rowid = ?")) {
                    delete.setLong(1, document.getId());
                    delete.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO " + TABLE + " (rowid, embedding) VALUES (?, ?)")) {
                    insert.setLong(1, document.getId());
                    insert.setString(2, document.getVectorJson());
                    insert.executeUpdate();
                }
                connection.commit();
            } catch (SQLException ex) {
                rollbackQuietly();
                log.warn("sqlite-vec upsert failed for document {}: {}", document.getId(), ex.getMessage());
                disable();
            } finally {
                restoreAutoCommit();
            }
        }
    }

    public void delete(long documentId) {
        synchronized (lock) {
            if (!ensureInitialized()) {
                return;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM " + TABLE + " WHERE rowid = ?")) {
                statement.setLong(1, documentId);
                statement.executeUpdate();
            } catch (SQLException ex) {
                log.warn("sqlite-vec delete failed for document {}: {}", documentId, ex.getMessage());
            }
        }
    }

    public List<Match> search(float[] vector, int limit) {
        synchronized (lock) {
            if (!ensureInitialized()) {
                return List.of();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT rowid, distance FROM " + TABLE
                            + " WHERE embedding MATCH ? AND k = ? ORDER BY distance")) {
                statement.setString(1, objectMapper.writeValueAsString(vector));
                statement.setInt(2, limit);
                List<Match> matches = new ArrayList<>();
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        matches.add(new Match(resultSet.getLong(1), resultSet.getDouble(2)));
                    }
                }
                return matches;
            } catch (Exception ex) {
                log.warn("sqlite-vec search failed, using Java cosine fallback: {}", ex.getMessage());
                disable();
                return List.of();
            }
        }
    }

    private boolean ensureInitialized() {
        if (initialized) {
            return available;
        }
        synchronized (lock) {
            if (initialized) {
                return available;
            }
            initialized = true;
            String url = environment.getProperty("spring.datasource.url", "");
            if (!url.startsWith("jdbc:sqlite:")) {
                log.info("sqlite-vec disabled: runtime database is not SQLite.");
                return false;
            }
            Path extension = resolveExtension();
            if (extension == null) {
                log.warn("sqlite-vec extension not found at '{}'. Vector search will use Java cosine.",
                        properties.getSqliteExtension());
                return false;
            }
            try {
                SQLiteConfig config = new SQLiteConfig();
                config.enableLoadExtension(true);
                config.setBusyTimeout(5000);
                connection = DriverManager.getConnection(url, config.toProperties());
                try (Statement statement = connection.createStatement()) {
                    statement.execute("SELECT load_extension('" + extension.toString().replace("'", "''") + "')");
                }
                ensureTable();
                available = true;
                log.info("sqlite-vec loaded from {}.", extension);
                return true;
            } catch (SQLException ex) {
                log.warn("sqlite-vec unavailable, vector search will use Java cosine: {}", ex.getMessage());
                closeQuietly();
                return false;
            }
        }
    }

    private void ensureTable() throws SQLException {
        String expected = "float[" + Math.max(32, properties.getDimension()) + "]";
        String existing = null;
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = '" + TABLE + "'")) {
            if (resultSet.next()) {
                existing = resultSet.getString(1);
            }
        }
        if (existing != null && !existing.toLowerCase(Locale.ROOT).contains(expected)) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DROP TABLE " + TABLE);
            }
            existing = null;
        }
        if (existing == null) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE VIRTUAL TABLE " + TABLE + " USING vec0(embedding " + expected + ")");
            }
        }
    }

    private Path resolveExtension() {
        String configured = properties.getSqliteExtension();
        if (configured == null || configured.isBlank()) {
            return null;
        }
        List<Path> candidates = new ArrayList<>();
        boolean hasSuffix = configured.endsWith(".dll") || configured.endsWith(".so") || configured.endsWith(".dylib");
        List<String> names = hasSuffix ? List.of(configured) : List.of(configured + suffix(), configured);
        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        for (String name : names) {
            Path path = Path.of(name);
            if (path.isAbsolute()) {
                candidates.add(path);
            } else {
                candidates.add(workingDirectory.resolve(path).normalize());
                candidates.add(workingDirectory.resolve("..").resolve(path).normalize());
                candidates.add(workingDirectory.resolve("backend").resolve(path).normalize());
                candidates.add(path.toAbsolutePath());
            }
        }
        return candidates.stream().filter(Files::isRegularFile).findFirst().orElse(null);
    }

    private String suffix() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return ".dll";
        }
        if (os.contains("mac")) {
            return ".dylib";
        }
        return ".so";
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // best effort
        }
    }

    private void restoreAutoCommit() {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException ignored) {
            // best effort
        }
    }

    private void disable() {
        available = false;
    }

    private void closeQuietly() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // best effort
            }
            connection = null;
        }
    }

    @PreDestroy
    void shutdown() {
        synchronized (lock) {
            closeQuietly();
        }
    }
}
