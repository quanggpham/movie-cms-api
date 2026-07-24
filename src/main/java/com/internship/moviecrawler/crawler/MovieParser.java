package com.internship.moviecrawler.crawler;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.internship.moviecrawler.model.Movie;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parses raw HTML into a Movie object.
 *
 * Primary strategy: Extract JSON-LD structured data (schema.org/Movie).
 * Fallback: CSS selectors on HTML body when JSON-LD is absent.
 *
 * Only title is REQUIRED — failure to extract title throws ParseException.
 * All other fields default gracefully (null, "", or empty list).
 */
public class MovieParser {

    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19|20)\\d{2}\\b");

    // === HTML fallback selectors (used when JSON-LD absent) ===
    private static final String SEL_TITLE = "h1";
    private static final String SEL_YEAR = "p.mt-1";
    private static final String SEL_GENRES = "div[aria-label=Thể loại] a";
    private static final String SEL_COUNTRY = "span.country";

    private final Gson gson = new Gson();

    /**
     * Parse raw HTML into a Movie object.
     *
     * @param html raw HTML string
     * @param url  source URL (stored in Movie for dedup)
     * @return populated Movie (never null)
     * @throws ParseException if title cannot be extracted
     */
    public Movie parse(String html, String url) throws ParseException {
        if (html == null || html.isBlank()) {
            throw new ParseException("HTML is null or empty for URL: " + url);
        }

        Document doc = Jsoup.parse(html);

        // === Strategy 1: JSON-LD (primary — accurate, structured) ===
        JsonObject jsonLd = extractMovieJsonLd(doc);
        if (jsonLd != null) {
            return parseFromJsonLd(jsonLd, url);
        }

        // === Strategy 2: HTML CSS selectors (fallback) ===
        return parseFromHtml(doc, url);
    }

    // ---- JSON-LD parsing ----

    private JsonObject extractMovieJsonLd(Document doc) {
        Elements scripts = doc.select("script[type=\"application/ld+json\"]");
        for (Element script : scripts) {
            try {
                JsonElement el = JsonParser.parseString(script.data());
                if (el.isJsonObject()) {
                    JsonObject obj = el.getAsJsonObject();
                    String type = obj.has("@type") ? obj.get("@type").getAsString() : "";
                    if ("Movie".equals(type)) {
                        return obj;
                    }
                }
            } catch (Exception ignored) {
                // Skip malformed JSON-LD blocks
            }
        }
        return null;
    }

    private Movie parseFromJsonLd(JsonObject json, String url) throws ParseException {
        // Title — REQUIRED
        String title = getString(json, "name");
        if (title == null || title.isBlank()) {
            throw new ParseException("No title in JSON-LD for URL: " + url);
        }

        // Release year
        Integer releaseYear = null;
        String dateCreated = getString(json, "dateCreated");
        if (dateCreated != null) {
            Matcher m = YEAR_PATTERN.matcher(dateCreated);
            if (m.find()) {
                releaseYear = Integer.parseInt(m.group());
            }
        }

        // Country — not in standard schema.org/Movie JSON-LD, default to ""
        String country = "";

        // Genres
        List<String> genres = getStringList(json, "genre");

        // Directors
        List<String> directors = getPersonList(json, "director");

        // Actors
        List<String> actors = getPersonList(json, "actor");

        return new Movie(url, title, releaseYear, country, genres, directors, actors);
    }

    // ---- HTML fallback parsing ----

    private Movie parseFromHtml(Document doc, String url) throws ParseException {
        // Title
        String title = extractTitle(doc, url);

        // Year
        Integer year = extractYear(doc);

        // Country
        String country = extractCountry(doc);

        // Genres
        List<String> genres = extractList(doc, SEL_GENRES);

        // Directors — try tab "Đạo diễn & diễn viên"
        List<String> directors = extractDirectorsFromTab(doc);

        // Actors
        List<String> actors = extractActorsFromTab(doc);

        return new Movie(url, title, year, country, genres, directors, actors);
    }

    private String extractTitle(Document doc, String url) throws ParseException {
        // Try <title> tag
        String title = doc.title();
        if (title != null && !title.isBlank()) {
            // Strip site suffix: " — ToiVote"
            int dash = title.indexOf(" — ");
            if (dash > 0) {
                title = title.substring(0, dash).trim();
            }
            if (!title.isBlank()) return title;
        }
        // Try h1
        Elements h1s = doc.select(SEL_TITLE);
        if (!h1s.isEmpty()) {
            String h1Text = h1s.first().text();
            if (!h1Text.isBlank()) return h1Text.trim();
        }
        throw new ParseException("No title found for URL: " + url);
    }

    private Integer extractYear(Document doc) {
        Elements elements = doc.select(SEL_YEAR);
        for (Element el : elements) {
            Matcher m = YEAR_PATTERN.matcher(el.text());
            if (m.find()) {
                return Integer.parseInt(m.group());
            }
        }
        // Fallback: search body text
        String bodyText = doc.body() != null ? doc.body().text() : "";
        Matcher m = YEAR_PATTERN.matcher(bodyText);
        if (m.find()) {
            return Integer.parseInt(m.group());
        }
        return null;
    }

    private String extractCountry(Document doc) {
        Elements elements = doc.select(SEL_COUNTRY);
        if (!elements.isEmpty()) {
            return elements.first().text().trim();
        }
        return "";
    }

    private List<String> extractList(Document doc, String selector) {
        Elements elements = doc.select(selector);
        if (elements.isEmpty()) return Collections.emptyList();
        return elements.stream()
                .map(Element::text)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private List<String> extractDirectorsFromTab(Document doc) {
        // Look for "Đạo diễn" paragraph → following links with person names
        Elements directorSections = doc.select("p:containsOwn(Đạo diễn)");
        if (!directorSections.isEmpty()) {
            Element parent = directorSections.first().parent();
            Elements links = parent.select("a p.mt-1, a p");
            return links.stream()
                    .map(Element::text)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private List<String> extractActorsFromTab(Document doc) {
        // Look for "Diễn viên" paragraph → following links with person names
        Elements actorSections = doc.select("p:containsOwn(Diễn viên)");
        if (!actorSections.isEmpty()) {
            Element parent = actorSections.first().parent();
            Elements links = parent.select("a p.mt-1, a p");
            return links.stream()
                    .map(Element::text)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    // ---- JSON helpers ----

    private String getString(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return null;
        JsonElement el = obj.get(key);
        return el.isJsonPrimitive() ? el.getAsString() : null;
    }

    private List<String> getStringList(JsonObject obj, String key) {
        if (!obj.has(key)) return Collections.emptyList();
        JsonElement el = obj.get(key);
        if (el.isJsonArray()) {
            List<String> result = new ArrayList<>();
            for (JsonElement item : el.getAsJsonArray()) {
                if (item.isJsonPrimitive()) {
                    result.add(item.getAsString().trim());
                }
            }
            return result;
        }
        // Single value
        if (el.isJsonPrimitive()) {
            return List.of(el.getAsString().trim());
        }
        return Collections.emptyList();
    }

    private List<String> getPersonList(JsonObject obj, String key) {
        if (!obj.has(key)) return Collections.emptyList();
        JsonElement el = obj.get(key);
        if (el.isJsonArray()) {
            List<String> result = new ArrayList<>();
            for (JsonElement item : el.getAsJsonArray()) {
                if (item.isJsonObject() && item.getAsJsonObject().has("name")) {
                    result.add(item.getAsJsonObject().get("name").getAsString().trim());
                }
            }
            return result;
        }
        // Single person object
        if (el.isJsonObject() && el.getAsJsonObject().has("name")) {
            return List.of(el.getAsJsonObject().get("name").getAsString().trim());
        }
        return Collections.emptyList();
    }
}
