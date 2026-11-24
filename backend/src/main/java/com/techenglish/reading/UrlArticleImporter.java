package com.techenglish.reading;

import com.techenglish.common.ApiException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

@Component
public class UrlArticleImporter {

    private static final int MAX_BYTES = 2_000_000;

    public ImportedArticle fetch(String rawUrl) {
        URI uri = parseAndValidate(rawUrl);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        try {
            for (int redirect = 0; redirect < 5; redirect++) {
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(15))
                        .header("User-Agent", "TechEnglish/0.1 (local learning app)")
                        .header("Accept", "text/html,application/xhtml+xml,text/plain")
                        .header("Accept-Encoding", "identity")
                        .GET()
                        .build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                if (status >= 300 && status < 400) {
                    String location = response.headers().firstValue("Location").orElse(null);
                    if (location == null) {
                        throw ApiException.badRequest("URL_REDIRECT", "网页重定向缺少目标地址");
                    }
                    uri = parseAndValidate(uri.resolve(location).toString());
                    continue;
                }
                if (status < 200 || status >= 300) {
                    throw ApiException.badRequest("URL_FETCH_FAILED", "网页返回状态码 " + status);
                }
                String contentType = response.headers().firstValue("Content-Type").orElse("");
                if (!contentType.isBlank()
                        && !contentType.toLowerCase(Locale.ROOT).contains("text/html")
                        && !contentType.toLowerCase(Locale.ROOT).contains("text/plain")
                        && !contentType.toLowerCase(Locale.ROOT).contains("xhtml")) {
                    throw ApiException.badRequest("URL_UNSUPPORTED", "目前只支持导入网页文本，不支持 PDF 或二进制内容");
                }
                String body = readBody(response.body());
                if (contentType.toLowerCase(Locale.ROOT).contains("text/plain")) {
                    return new ImportedArticle(uri.toString(), null, body);
                }
                return parseHtml(uri.toString(), body);
            }
            throw ApiException.badRequest("URL_TOO_MANY_REDIRECTS", "网页重定向次数过多");
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.badRequest("URL_FETCH_FAILED", "无法读取这个网页，请改用粘贴文本");
        }
    }

    private URI parseAndValidate(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                throw ApiException.badRequest("URL_INVALID", "只支持 http/https 地址");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw ApiException.badRequest("URL_INVALID", "网址缺少主机名");
            }
            InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
            for (InetAddress address : addresses) {
                if (isPrivate(address)) {
                    throw ApiException.badRequest("URL_BLOCKED", "出于安全原因，不能导入内网地址");
                }
            }
            return uri;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.badRequest("URL_INVALID", "网址格式不正确");
        }
    }

    private boolean isPrivate(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            if (first == 100 && second >= 64 && second <= 127) {
                return true;
            }
            if (first == 0) {
                return true;
            }
        }
        if (bytes.length == 16) {
            int first = bytes[0] & 0xff;
            return (first & 0xfe) == 0xfc;
        }
        return false;
    }

    private String readBody(InputStream inputStream) throws Exception {
        try (InputStream in = inputStream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BYTES) {
                    throw ApiException.badRequest("URL_TOO_LARGE", "网页内容过大，请粘贴需要的部分");
                }
                out.write(buffer, 0, read);
            }
            return out.toString(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private ImportedArticle parseHtml(String url, String html) {
        Document document = Jsoup.parse(html, url);
        document.select("script,style,nav,footer,aside,header,form,noscript,svg").remove();
        Element content = document.selectFirst("article");
        if (content == null) {
            content = document.selectFirst("main");
        }
        if (content == null) {
            content = document.body();
        }
        StringBuilder markdown = new StringBuilder();
        if (document.title() != null && !document.title().isBlank()) {
            markdown.append("# ").append(document.title().trim()).append("\n\n");
        }
        for (Element element : content.select("h1,h2,h3,p,li,pre,blockquote")) {
            if (element.parents().stream().anyMatch(parent -> parent.tagName().equals("li"))
                    && element.tagName().equals("p")) {
                continue;
            }
            String text = element.tagName().equals("pre")
                    ? element.wholeText().trim()
                    : element.text().trim();
            if (text.isBlank()) {
                continue;
            }
            switch (element.tagName()) {
                case "h1" -> markdown.append("\n## ").append(text).append("\n\n");
                case "h2" -> markdown.append("\n## ").append(text).append("\n\n");
                case "h3" -> markdown.append("\n### ").append(text).append("\n\n");
                case "li" -> markdown.append("- ").append(text).append("\n");
                case "pre" -> markdown.append("\n```\n").append(text).append("\n```\n\n");
                case "blockquote" -> markdown.append("\n> ").append(text).append("\n\n");
                default -> markdown.append(text).append("\n\n");
            }
        }
        String result = markdown.toString().trim();
        if (result.length() < 80) {
            result = document.body().text();
        }
        return new ImportedArticle(url, document.title(), result);
    }

    public record ImportedArticle(String url, String title, String markdown) {
    }
}
