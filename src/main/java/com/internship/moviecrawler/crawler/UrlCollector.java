package com.internship.moviecrawler.crawler;

import com.internship.moviecrawler.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * <p>Standalone usage:
 * <pre>{@code java -cp <fat-jar> com.internship.moviecrawler.crawler.UrlCollector}</pre>
 * Writes discovered URLs to {@code src/main/resources/urls.txt} (classpath resource).
 */
public class UrlCollector {

    private static final Logger log = LoggerFactory.getLogger(UrlCollector.class);

    private static final Pattern MOVIE_URL_PATTERN =
            Pattern.compile("/movie/([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})");

    private static final String[] SEED_PAGES = {
            "https://toivote.com/",
            "https://toivote.com/leaderboard",
            "https://toivote.com/discover/genres"
    };

    /**
     * Entry point for standalone URL discovery.
     * Reads config, creates a MovieFetcher, discovers URLs, writes to urls.txt resource.
     */
    public static void main(String[] args) {
        AppConfig config = new AppConfig();
        MovieFetcher fetcher = new MovieFetcher(
                config.getFetchConnectTimeoutMs(),
                config.getFetchRequestTimeoutMs(),
                config.getFetchMaxRetries(),
                config.getFetchUserAgent()
        );

        // Write to src/main/resources/urls.txt (classpath resource read by App.loadUrls)
        Path outputFile = Path.of("src/main/resources/urls.txt");
        try {
            Files.createDirectories(outputFile.getParent());
            Set<String> urls = collect(fetcher, outputFile);
            log.info("Done — {} URLs written to {}", urls.size(), outputFile);
        } catch (IOException e) {
            log.error("Failed to write urls.txt", e);
        }
    }

    /**
     * Fetch seed pages and extract unique movie detail URLs.
     *
     * @param fetcher    MovieFetcher instance for HTTP requests
     * @param outputFile path to write discovered URLs
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
                log.warn("Skipping {} — {}", pageUrl, e.getMessage());
            } catch (FetchException.TransientFetchException e) {
                log.warn("Transient error for {} — {}", pageUrl, e.getMessage());
            } catch (FetchException e) {
                log.warn("Error fetching {} — {}", pageUrl, e.getMessage());
            }
        }

        int after = urls.size();
        if (after > before) {
            log.info("Discovered {} new URLs", after - before);
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
