package com.techenglish.vector;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class VectorSearchIntegrationTest {

    @Autowired
    private VectorSearchService vectorSearchService;

    @Test
    void indexesSeedContentAndFindsTheDatabaseArticle() {
        VectorDtos.SearchResponse response = vectorSearchService.search("database index query", 5);

        assertThat(response.results()).isNotEmpty();
        assertThat(response.results())
                .anySatisfy(result -> assertThat(result.title()).containsIgnoringCase("database"));
        assertThat(response.mode()).isIn("VECTOR", "VECTOR_JAVA", "KEYWORD");
    }

    @Test
    void reportsVectorStatus() {
        VectorDtos.VectorStatus status = vectorSearchService.status();

        assertThat(status.enabled()).isTrue();
        assertThat(status.dimension()).isEqualTo(384);
        assertThat(status.documents()).isGreaterThan(0);
        assertThat(status.index()).isIn("SQLITE_VEC", "JAVA_COSINE");
    }
}
