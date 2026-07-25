package com.internship.moviecrawler.crawler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auto-discovers movie URLs from toivote.com by fetching known pages
 * and extracting /movie/{uuid} links from the HTML.
 *
 * Usage: {@code UrlCollector.collect(fetcher, outputPath)}
 */
public class UrlCollector {

    private static final Pattern MOVIE_URL_PATTERN =
            Pattern.compile("/movie/([a-f0-9]{8,})");

    private static final String[] SEED_PAGES = {
            "https://toivote.com/",
            "https://toivote.com/leaderboard"
    };

    /**
     * Fetch seed pages and extract unique movie detail URLs.
     *
     * @param fetcher    MovieFetcher instance for HTTP requests
     * @param outputFile path to write discovered URLs (appends, doesn't overwrite)
     * @return set of discovered URLs
     */
    public static Set<String> collect(MovieFetcher fetcher, Path outputFile) throws IOException {
        Set<String> urls = new LinkedHashSet<>();

        // 1. Read existing URLs from file (preserve manually added ones)
        if (Files.exists(outputFile)) {
            Files.readAllLines(outputFile).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#") && line.startsWith("https://"))
                    .forEach(urls::add);
        }

        int before = urls.size();

        // 2. Fetch seed pages and extract movie links
        for (String pageUrl : SEED_PAGES) {
            try {
                String html = fetcher.fetch(pageUrl);
                extractMovieUrls(html, urls);
            } catch (FetchException.PermanentFetchException e) {
                System.err.println("[UrlCollector] Skipping " + pageUrl + " — " + e.getMessage());
            } catch (FetchException.TransientFetchException e) {
                System.err.println("[UrlCollector] Transient error for " + pageUrl + " — " + e.getMessage());
            } catch (FetchException e) {
                System.err.println("[UrlCollector] Error fetching " + pageUrl + " — " + e.getMessage());
            }
        }

        int after = urls.size();
        if (after > before) {
            System.out.println("[UrlCollector] Discovered " + (after - before) + " new URLs");
        }

        // 3. Write back to file
        Files.writeString(outputFile,
                "# Auto-discovered movie URLs from toivote.com\n" +
                "# Run UrlCollector.main() to refresh this list\n" +
                "# " + after + " URLs total\n\n" +
                String.join("\n", urls) + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        return Collections.unmodifiableSet(urls);
    }

    /**
     * Extract /movie/{uuid} links from HTML and convert to full URLs.
     */
    static Set<String> extractMovieUrls(String html, Set<String> target) {
        Matcher m = MOVIE_URL_PATTERN.matcher(html);
        while (m.find()) {
            target.add("https://toivote.com/movie/" + m.group(1));
        }
        return target;
    }
}
