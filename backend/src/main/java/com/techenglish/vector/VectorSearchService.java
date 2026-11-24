package com.techenglish.vector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techenglish.common.ApiException;
import com.techenglish.reading.ReadingArticle;
import com.techenglish.reading.ReadingArticleRepository;
import com.techenglish.vocabulary.VocabEntry;
import com.techenglish.vocabulary.VocabEntryRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@Service
@Order(100)
@RequiredArgsConstructor
public class VectorSearchService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchService.class);
    private static final String ARTICLE = "ARTICLE";
    private static final String VOCAB = "VOCAB";
    private static final double MIN_SCORE = 0.05;
    private static final int SNIPPET_LENGTH = 360;

    private final VectorProperties properties;
    private final EmbeddingService embeddingService;
    private final SqliteVecIndex sqliteVecIndex;
    private final EmbeddingDocumentRepository documentRepository;
    private final ReadingArticleRepository articleRepository;
    private final VocabEntryRepository vocabRepository;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper;

    private TransactionTemplate transactionTemplate;

    @PostConstruct
    void initTransactions() {
        // AFTER_COMMIT listeners still see the completed transaction context,
        // so indexing must open a fresh one instead of joining it.
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.info("Vector search disabled by configuration.");
            return;
        }
        if (!embeddingService.configured()) {
            log.warn("Vector search is enabled but the embedding provider is not configured; "
                    + "search will use keyword matching until a key is provided.");
        }
        try {
            int chunks = 0;
            for (ReadingArticle article : articleRepository.findAll()) {
                chunks += indexArticleInternal(article, false);
            }
            int words = 0;
            for (VocabEntry word : vocabRepository.findAllByOrderByIdAsc()) {
                indexVocab(word);
                words++;
            }
            rebuildVectorIndex();
            log.info("Vector store ready: {} article chunk(s), {} word(s), model {}, index {}",
                    chunks, words, embeddingService.modelName(),
                    sqliteVecIndex.available() ? "sqlite-vec" : "java-cosine");
        } catch (RuntimeException ex) {
            log.warn("Vector indexing skipped at startup: {}", ex.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onArticleImported(ArticleImportedEvent event) {
        try {
            if (properties.isEnabled()) {
                indexArticle(event.articleId());
            }
        } catch (RuntimeException ex) {
            log.warn("Article {} could not be indexed: {}", event.articleId(), ex.getMessage());
        }
    }

    public int indexArticle(Long articleId) {
        ReadingArticle article = inTransaction(() -> articleRepository.findById(articleId).orElse(null));
        if (article == null) {
            return 0;
        }
        int chunks = indexArticleInternal(article, true);
        log.info("Indexed article {} into {} chunk(s).", articleId, chunks);
        return chunks;
    }

    private int indexArticleInternal(ReadingArticle article, boolean syncIndex) {
        List<TextChunker.Chunk> chunks = TextChunker.chunks(article.getContentMd());
        String model = embeddingService.modelName();
        int dimension = embeddingService.dimension();
        for (TextChunker.Chunk chunk : chunks) {
            String hash = sha256(model + "|" + chunk.text());
            EmbeddingDocument existing = inTransaction(() -> documentRepository
                    .findBySourceTypeAndSourceIdAndChunkIndexAndModel(ARTICLE, article.getId(), chunk.index(), model)
                    .orElse(null));
            if (existing != null
                    && hash.equals(existing.getContentHash())
                    && existing.getDimension() == dimension) {
                if (syncIndex) {
                    sqliteVecIndex.upsert(existing);
                }
                continue;
            }
            float[] vector = embeddingService.embed(chunk.text());
            String vectorJson = toJson(vector);
            EmbeddingDocument saved = inTransaction(() -> {
                EmbeddingDocument document = existing != null ? existing : new EmbeddingDocument();
                document.setSourceType(ARTICLE);
                document.setSourceId(article.getId());
                document.setChunkIndex(chunk.index());
                document.setTitle(truncate(article.getTitle(), 240));
                document.setSnippet(truncate(chunk.text(), SNIPPET_LENGTH));
                document.setContentHash(hash);
                document.setModel(model);
                document.setDimension(dimension);
                document.setVectorJson(vectorJson);
                return documentRepository.saveAndFlush(document);
            });
            if (syncIndex) {
                sqliteVecIndex.upsert(saved);
            }
        }
        deleteStaleChunks(ARTICLE, article.getId(), chunks.size(), model);
        return chunks.size();
    }

    private void indexVocab(VocabEntry word) {
        String model = embeddingService.modelName();
        int dimension = embeddingService.dimension();
        String text = buildVocabText(word);
        String hash = sha256(model + "|" + text);
        EmbeddingDocument existing = inTransaction(() -> documentRepository
                .findBySourceTypeAndSourceIdAndChunkIndexAndModel(VOCAB, word.getId(), 0, model)
                .orElse(null));
        if (existing != null && hash.equals(existing.getContentHash()) && existing.getDimension() == dimension) {
            return;
        }
        float[] vector = embeddingService.embed(text);
        String vectorJson = toJson(vector);
        inTransaction(() -> {
            EmbeddingDocument document = existing != null ? existing : new EmbeddingDocument();
            document.setSourceType(VOCAB);
            document.setSourceId(word.getId());
            document.setChunkIndex(0);
            document.setTitle(truncate(word.getLemma(), 240));
            document.setSnippet(truncate(word.getDefinitionEn() + " — " + word.getDefinitionZh(), SNIPPET_LENGTH));
            document.setContentHash(hash);
            document.setModel(model);
            document.setDimension(dimension);
            document.setVectorJson(vectorJson);
            return documentRepository.saveAndFlush(document);
        });
    }

    private String buildVocabText(VocabEntry word) {
        StringBuilder builder = new StringBuilder(word.getLemma());
        if (word.getPos() != null && !word.getPos().isBlank()) {
            builder.append(" (").append(word.getPos()).append(')');
        }
        builder.append(". ").append(word.getDefinitionEn());
        if (word.getExampleEn() != null && !word.getExampleEn().isBlank()) {
            builder.append(' ').append(word.getExampleEn());
        }
        builder.append(' ').append(word.getDefinitionZh());
        return builder.toString();
    }

    private void deleteStaleChunks(String sourceType, Long sourceId, int chunkCount, String model) {
        List<EmbeddingDocument> documents = inTransaction(() -> documentRepository
                .findBySourceTypeAndSourceIdAndModelOrderByChunkIndexAsc(sourceType, sourceId, model));
        for (EmbeddingDocument document : documents) {
            if (document.getChunkIndex() >= chunkCount) {
                inTransaction(() -> {
                    documentRepository.delete(document);
                    return null;
                });
                sqliteVecIndex.delete(document.getId());
            }
        }
    }

    private void rebuildVectorIndex() {
        if (!sqliteVecIndex.available()) {
            return;
        }
        String model = embeddingService.modelName();
        List<EmbeddingDocument> documents = inTransaction(
                () -> documentRepository.findByModelOrderByIdAsc(model));
        if (!documents.isEmpty()) {
            sqliteVecIndex.rebuild(documents);
        }
    }

    public VectorDtos.SearchResponse search(String rawQuery, int requestedLimit) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isBlank()) {
            throw ApiException.badRequest("QUERY_REQUIRED", "请输入要搜索的内容");
        }
        int limit = Math.max(1, Math.min(requestedLimit, properties.getMaxResults()));
        if (!properties.isEnabled()) {
            return response(query, "DISABLED", List.of());
        }
        String model = embeddingService.modelName();
        List<EmbeddingDocument> documents = inTransaction(
                () -> documentRepository.findByModelOrderByIdAsc(model));
        if (documents.isEmpty()) {
            return response(query, "EMPTY", List.of());
        }

        float[] queryVector = null;
        try {
            queryVector = embeddingService.embed(query);
        } catch (RuntimeException ex) {
            log.warn("Query embedding failed, using keyword search: {}", ex.getMessage());
        }

        List<Scored> scored = List.of();
        String mode = "KEYWORD";
        if (queryVector != null) {
            mode = sqliteVecIndex.available() ? "VECTOR" : "VECTOR_JAVA";
            scored = vectorSearch(query, queryVector, limit, documents);
        }
        if (scored.isEmpty()) {
            mode = queryVector == null ? "KEYWORD" : mode;
            scored = keywordSearch(query, limit, documents);
            if (queryVector != null && !scored.isEmpty()) {
                mode = "KEYWORD";
            }
        }
        return response(query, mode, scored);
    }

    private List<Scored> vectorSearch(String query, float[] queryVector, int limit,
                                      List<EmbeddingDocument> documents) {
        Map<Long, EmbeddingDocument> byId = new HashMap<>();
        for (EmbeddingDocument document : documents) {
            byId.put(document.getId(), document);
        }
        List<Scored> scored = new ArrayList<>();
        if (sqliteVecIndex.available()) {
            List<SqliteVecIndex.Match> matches = sqliteVecIndex.search(queryVector, Math.min(60, limit * 4));
            for (SqliteVecIndex.Match match : matches) {
                EmbeddingDocument document = byId.get(match.documentId());
                if (document == null) {
                    continue;
                }
                double cosine = 1.0 - (match.distance() * match.distance()) / 2.0;
                double score = cosine + lexicalBoost(query, document);
                if (score >= MIN_SCORE) {
                    scored.add(new Scored(document, score));
                }
            }
        }
        if (scored.isEmpty()) {
            for (EmbeddingDocument document : documents) {
                double cosine = HashingEmbedder.cosine(queryVector, fromJson(document.getVectorJson()));
                double score = cosine + lexicalBoost(query, document);
                if (score >= MIN_SCORE) {
                    scored.add(new Scored(document, score));
                }
            }
        }
        return dedupeAndSort(scored, limit);
    }

    /**
     * Hashing embeddings are lexical; a small exact-term bonus keeps rare jargon
     * findable without overpowering semantic similarity when a real model is used.
     */
    private double lexicalBoost(String query, EmbeddingDocument document) {
        Set<String> terms = tokens(query);
        if (terms.isEmpty()) {
            return 0;
        }
        Set<String> documentTerms = tokens(document.getTitle() + " " + document.getSnippet());
        int overlap = 0;
        for (String term : terms) {
            if (documentTerms.contains(term)) {
                overlap++;
            }
        }
        return 0.35 * overlap / terms.size();
    }

    private List<Scored> keywordSearch(String query, int limit, List<EmbeddingDocument> documents) {
        Set<String> terms = tokens(query);
        if (terms.isEmpty()) {
            return List.of();
        }
        List<Scored> scored = new ArrayList<>();
        for (EmbeddingDocument document : documents) {
            Set<String> docTerms = tokens(document.getTitle() + " " + document.getSnippet());
            int overlap = 0;
            for (String term : terms) {
                if (docTerms.contains(term)) {
                    overlap++;
                }
            }
            if (overlap == 0) {
                continue;
            }
            double score = (double) overlap / terms.size();
            if (document.getTitle().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) {
                score += 0.3;
            }
            scored.add(new Scored(document, score));
        }
        return dedupeAndSort(scored, limit);
    }

    private List<Scored> dedupeAndSort(List<Scored> scored, int limit) {
        Map<String, Scored> best = new LinkedHashMap<>();
        scored.stream()
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .forEach(item -> {
                    String key = item.document().getSourceType() + ":" + item.document().getSourceId();
                    Scored previous = best.get(key);
                    if (previous == null || item.score() > previous.score()) {
                        best.put(key, item);
                    }
                });
        return best.values().stream()
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(limit)
                .toList();
    }

    public VectorDtos.VectorStatus status() {
        String model = embeddingService.modelName();
        long documents = inTransaction(() -> documentRepository.countByModel(model));
        String index;
        if (!properties.isEnabled()) {
            index = "DISABLED";
        } else if (sqliteVecIndex.available()) {
            index = "SQLITE_VEC";
        } else {
            index = "JAVA_COSINE";
        }
        return new VectorDtos.VectorStatus(
                properties.isEnabled(),
                embeddingService.providerName(),
                model,
                embeddingService.dimension(),
                documents,
                index,
                embeddingService.configured()
        );
    }

    private VectorDtos.SearchResponse response(String query, String mode, List<Scored> scored) {
        List<VectorDtos.SearchResult> results = scored.stream()
                .map(item -> new VectorDtos.SearchResult(
                        item.document().getSourceType(),
                        item.document().getSourceId(),
                        item.document().getTitle(),
                        item.document().getSnippet(),
                        Math.round(item.score() * 1000.0) / 1000.0
                ))
                .toList();
        return new VectorDtos.SearchResponse(
                query,
                mode,
                embeddingService.providerName(),
                embeddingService.modelName(),
                embeddingService.dimension(),
                sqliteVecIndex.available(),
                results
        );
    }

    private record Scored(EmbeddingDocument document, double score) {
    }

    private Set<String> tokens(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (token.length() > 1) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private String toJson(float[] vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot serialize embedding", ex);
        }
    }

    private float[] fromJson(String json) {
        try {
            return objectMapper.readValue(json, float[].class);
        } catch (Exception ex) {
            return new float[0];
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max - 1) + "…";
    }

    private <T> T inTransaction(Supplier<T> supplier) {
        return transactionTemplate.execute(status -> supplier.get());
    }
}
