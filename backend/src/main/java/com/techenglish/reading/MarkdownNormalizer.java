package com.techenglish.reading;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalises markdown imported from doc tools (Google style guide exports,
 * Doxygen, Confluence ...). Those files carry empty HTML anchors such as
 * {@code <a id="s2.1-foo"></a>} plus a table of contents that links to them
 * via {@code [2.1 Foo](#s2.1-foo)}. The reader renders markdown with raw HTML
 * disabled, so the anchors appear as literal text and the TOC links have no
 * target. This class removes the anchor tags and retargets the links to the
 * heading ids the frontend actually generates (see the slugify contract in
 * frontend/src/utils/markdown.ts: "h-" + lowercased, punctuation-collapsed).
 *
 * <p>Processing is fence-aware: lines inside ``` code blocks are never touched.
 */
public final class MarkdownNormalizer {

    /** Empty anchor with id/name, tolerant of attribute order and stray whitespace. */
    private static final Pattern EMPTY_ANCHOR = Pattern.compile(
            "<a\\b[^>]*?\\s(?:id|name)=\"([^\"]*)\"[^>]*>\\s*(?:<br\\s*/?>\\s*)*</a>",
            Pattern.CASE_INSENSITIVE);
    /** HTML link that only points to an in-page anchor: rewritten as markdown link. */
    private static final Pattern ANCHOR_LINK = Pattern.compile(
            "<a\\b[^>]*?\\shref=\"#([^\"]*)\"[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADING = Pattern.compile("^(#{2,5})\\s+(.+)$");
    private static final Pattern LINK_TARGET = Pattern.compile("\\]\\(#([^)]*)\\)");
    private static final Pattern SLUG_STRIP = Pattern.compile("[^\\w\\u4e00-\\u9fa5]+");
    private static final Pattern INLINE_MARKUP = Pattern.compile("[*_`]");

    private MarkdownNormalizer() {
    }

    public static String normalize(String markdown) {
        if (markdown == null || !markdown.toLowerCase(Locale.ROOT).contains("<a")) {
            return markdown;
        }
        String[] lines = markdown.split("\n", -1);
        Map<String, String> slugById = mapAnchorsToHeadingSlugs(lines);

        StringBuilder result = new StringBuilder(markdown.length());
        boolean inFence = false;
        boolean inComment = false;
        for (String line : lines) {
            if (isFence(line)) {
                inFence = !inFence;
                result.append(line).append('\n');
                continue;
            }
            if (inFence) {
                result.append(line).append('\n');
                continue;
            }
            if (inComment) {
                int end = line.indexOf("-->");
                if (end < 0) {
                    continue;
                }
                inComment = false;
                line = line.substring(end + 3);
            }
            int open = line.indexOf("<!--");
            if (open >= 0 && line.indexOf("-->", open) < 0) {
                inComment = true;
                line = line.substring(0, open);
            } else if (open >= 0) {
                line = line.substring(0, open) + line.substring(line.indexOf("-->", open) + 3);
            }
            line = ANCHOR_LINK.matcher(line).replaceAll("[$2](#$1)");
            line = EMPTY_ANCHOR.matcher(line).replaceAll("");
            line = retargetLinks(line, slugById);
            result.append(line).append('\n');
        }
        return result.toString().replaceAll("\n{3,}", "\n\n").trim();
    }

    /** Maps every empty-anchor id to the frontend slug of the heading it precedes. */
    private static Map<String, String> mapAnchorsToHeadingSlugs(String[] lines) {
        Map<String, String> slugById = new LinkedHashMap<>();
        boolean inFence = false;
        for (int i = 0; i < lines.length; i++) {
            if (isFence(lines[i])) {
                inFence = !inFence;
                continue;
            }
            if (inFence || !lines[i].contains("<a ")) {
                continue;
            }
            Matcher anchor = EMPTY_ANCHOR.matcher(lines[i]);
            while (anchor.find()) {
                String id = anchor.group(1);
                String slug = headingSlugAfter(lines, i + 1);
                if (slug != null && !id.equals(slug)) {
                    slugById.putIfAbsent(id, slug);
                }
            }
        }
        return slugById;
    }

    /**
     * Slug of the first level-2..5 heading following the anchor. Blank lines,
     * other empty anchors and trailing HTML comments are skipped; the first
     * remaining content line must be the heading, so a distant heading is
     * never mis-mapped.
     */
    private static String headingSlugAfter(String[] lines, int from) {
        for (int i = from; i < Math.min(lines.length, from + 6); i++) {
            if (isFence(lines[i])) {
                return null;
            }
            String candidate = EMPTY_ANCHOR.matcher(stripComment(lines[i])).replaceAll("").trim();
            if (candidate.isEmpty()) {
                continue;
            }
            Matcher heading = HEADING.matcher(candidate);
            if (heading.matches()) {
                return slugify(heading.group(2));
            }
            return null;
        }
        return null;
    }

    /** Drops an HTML comment on the line without state tracking (used for lookahead). */
    private static String stripComment(String line) {
        int start = line.indexOf("<!--");
        if (start < 0) {
            return line;
        }
        int end = line.indexOf("-->", start);
        return end < 0 ? line.substring(0, start) : line.substring(0, start) + line.substring(end + 3);
    }

    private static String retargetLinks(String line, Map<String, String> slugById) {
        if (slugById.isEmpty() || !line.contains("](#")) {
            return line;
        }
        Matcher link = LINK_TARGET.matcher(line);
        StringBuilder out = new StringBuilder();
        while (link.find()) {
            String target = slugById.getOrDefault(link.group(1), link.group(1));
            link.appendReplacement(out, Matcher.quoteReplacement("](#" + target + ")"));
        }
        link.appendTail(out);
        return out.toString();
    }

    /** Mirrors the anchor slugify in frontend/src/utils/markdown.ts exactly. */
    private static String slugify(String headingText) {
        String text = INLINE_MARKUP.matcher(headingText).replaceAll("")
                .trim()
                .toLowerCase(Locale.ROOT);
        String slug = SLUG_STRIP.matcher(text).replaceAll("-");
        return "h-" + slug.replaceAll("^-+|-+$", "");
    }

    private static boolean isFence(String line) {
        return line.trim().startsWith("```");
    }
}
