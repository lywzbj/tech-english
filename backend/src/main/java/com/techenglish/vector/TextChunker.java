package com.techenglish.vector;

import java.util.ArrayList;
import java.util.List;

/** Splits markdown into overlapping-free text chunks suitable for embeddings. */
public final class TextChunker {

    private static final int MAX_CHARS = 900;
    private static final int MIN_CHARS = 120;

    private TextChunker() {
    }

    public record Chunk(int index, String text) {
    }

    public static List<Chunk> chunks(String markdown) {
        List<Chunk> chunks = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) {
            return chunks;
        }
        String withoutCode = markdown.replaceAll("(?s)```.*?```", "\n\n");
        String heading = "";
        StringBuilder current = new StringBuilder();
        int index = 0;

        for (String block : withoutCode.split("\\n\\s*\\n")) {
            String trimmed = block.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.matches("^#{1,6}\\s+.*")) {
                if (current.length() >= MIN_CHARS) {
                    chunks.add(new Chunk(index++, compose(heading, current.toString())));
                    current.setLength(0);
                }
                heading = trimmed.replaceFirst("^#{1,6}\\s+", "").trim();
                continue;
            }
            String text = stripMarkup(trimmed);
            if (text.isBlank()) {
                continue;
            }
            for (String piece : splitLong(text)) {
                if (current.length() > 0 && current.length() + piece.length() + 1 > MAX_CHARS) {
                    chunks.add(new Chunk(index++, compose(heading, current.toString())));
                    current.setLength(0);
                }
                if (current.length() > 0) {
                    current.append(' ');
                }
                current.append(piece);
            }
        }
        if (current.length() > 0) {
            chunks.add(new Chunk(index, compose(heading, current.toString())));
        }
        return chunks;
    }

    private static String compose(String heading, String body) {
        String cleaned = body.trim().replaceAll("\\s+", " ");
        return heading.isBlank() ? cleaned : heading + ". " + cleaned;
    }

    private static List<String> splitLong(String text) {
        if (text.length() <= MAX_CHARS) {
            return List.of(text);
        }
        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : text.split("(?<=[.!?])\\s+")) {
            if (current.length() > 0 && current.length() + sentence.length() + 1 > MAX_CHARS) {
                pieces.add(current.toString());
                current.setLength(0);
            }
            if (sentence.length() > MAX_CHARS) {
                if (current.length() > 0) {
                    pieces.add(current.toString());
                    current.setLength(0);
                }
                for (int start = 0; start < sentence.length(); start += MAX_CHARS) {
                    pieces.add(sentence.substring(start, Math.min(sentence.length(), start + MAX_CHARS)));
                }
                continue;
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(sentence);
        }
        if (current.length() > 0) {
            pieces.add(current.toString());
        }
        return pieces;
    }

    private static String stripMarkup(String text) {
        return text
                .replaceAll("!\\[[^]]*]\\([^)]*\\)", " ")
                .replaceAll("\\[([^]]*)]\\([^)]*\\)", "$1")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("[*_`>#]+", " ")
                .replaceAll("(?m)^\\s*[-+*]\\s+", " ")
                .trim();
    }
}
