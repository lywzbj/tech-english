package com.techenglish.common.db;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies the SQLite schema because Flyway Community has no SQLite support.
 * MySQL keeps using Flyway; this bean is inactive under the mysql profile.
 */
@Component
@Profile("!mysql")
@RequiredArgsConstructor
public class SqliteSchemaMigrator implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(SqliteSchemaMigrator.class);
    private static final String MIGRATION_PATTERN = "classpath:db/migration/sqlite/V*__*.sql";

    private final DataSource dataSource;
    private final Environment environment;

    @Override
    public void afterSingletonsInstantiated() {
        String url = environment.getProperty("spring.datasource.url", "");
        if (!url.startsWith("jdbc:sqlite:")) {
            log.warn("SQLite migrator skipped: datasource is not SQLite ({}).", url);
            return;
        }
        ensureDatabaseDirectory(url);
        try {
            applyMigrations();
        } catch (IOException | SQLException ex) {
            throw new IllegalStateException("Failed to apply SQLite schema", ex);
        }
    }

    private void applyMigrations() throws IOException, SQLException {
        List<Resource> scripts = discoverScripts();
        if (scripts.isEmpty()) {
            throw new IllegalStateException("No SQLite migrations found at " + MIGRATION_PATTERN);
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            createVersionTable(connection);
            Set<String> applied = loadAppliedVersions(connection);
            for (Resource script : scripts) {
                String version = versionOf(script);
                if (applied.contains(version)) {
                    continue;
                }
                log.info("Applying SQLite migration {}", script.getFilename());
                try {
                    ScriptUtils.executeSqlScript(connection, new EncodedResource(script, StandardCharsets.UTF_8));
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO schema_version (version, applied_at) VALUES (?, ?)")) {
                        statement.setString(1, version);
                        statement.setString(2, Instant.now().toString());
                        statement.executeUpdate();
                    }
                    connection.commit();
                } catch (RuntimeException | SQLException ex) {
                    connection.rollback();
                    throw ex;
                }
            }
        }
        log.info("SQLite schema is up to date ({} migration(s) available).", scripts.size());
    }

    private List<Resource> discoverScripts() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources(MIGRATION_PATTERN);
        List<Resource> scripts = new ArrayList<>(List.of(resources));
        scripts.sort(Comparator.comparing(resource -> resource.getFilename() == null ? "" : resource.getFilename()));
        return scripts;
    }

    private void createVersionTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        version VARCHAR(40) PRIMARY KEY,
                        applied_at VARCHAR(40) NOT NULL
                    )
                    """);
        }
        connection.commit();
    }

    private Set<String> loadAppliedVersions(Connection connection) throws SQLException {
        Set<String> versions = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT version FROM schema_version")) {
            while (resultSet.next()) {
                versions.add(resultSet.getString(1));
            }
        }
        return versions;
    }

    private String versionOf(Resource script) {
        String filename = script.getFilename();
        if (filename == null) {
            throw new IllegalStateException("SQLite migration without a filename: " + script);
        }
        int separator = filename.indexOf("__");
        if (separator <= 0) {
            throw new IllegalStateException("SQLite migration must be named V<version>__<name>.sql: " + filename);
        }
        return filename.substring(0, separator);
    }

    private void ensureDatabaseDirectory(String url) {
        String rawPath = url.substring("jdbc:sqlite:".length());
        int query = rawPath.indexOf('?');
        if (query >= 0) {
            rawPath = rawPath.substring(0, query);
        }
        if (rawPath.isBlank() || rawPath.startsWith(":memory:") || rawPath.startsWith("file:")) {
            return;
        }
        Path parent = Path.of(rawPath).toAbsolutePath().getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create SQLite directory " + parent, ex);
        }
    }
}
