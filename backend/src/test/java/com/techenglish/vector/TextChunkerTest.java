package com.techenglish.vector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {

    @Test
    void splitsOnHeadingsAndKeepsHeadingContext() {
        String markdown = """
                # Title

                An introduction paragraph that is long enough to be kept as its own chunk.
                It explains the problem and why the reader should care about it.

                ## How it works

                The second section explains the mechanism in plain English. It also gives
                some detail so the chunk is meaningful for retrieval.

                ```java
                System.out.println("ignored");
                ```

                ### Details

                A final paragraph about trade-offs and operational concerns.
                """;

        List<TextChunker.Chunk> chunks = TextChunker.chunks(markdown);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).text()).contains("introduction paragraph");
        assertThat(chunks).anySatisfy(chunk -> assertThat(chunk.text()).contains("How it works"));
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.text()).doesNotContain("System.out"));
    }

    @Test
    void splitsLongParagraphsWithoutLosingContent() {
        String longText = ("A sentence about database indexes and query planning. ").repeat(40);

        List<TextChunker.Chunk> chunks = TextChunker.chunks(longText);

        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.text().length()).isLessThanOrEqualTo(901));
    }
}
