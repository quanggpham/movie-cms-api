package com.internship.moviecrawler;

import com.internship.moviecrawler.backup.DatabaseBackup;
import com.internship.moviecrawler.config.AppConfig;
import com.internship.moviecrawler.crawler.FetchException;
import com.internship.moviecrawler.crawler.MovieFetcher;
import com.internship.moviecrawler.crawler.MovieParser;
import com.internship.moviecrawler.crawler.ParseException;
import com.internship.moviecrawler.crawler.UrlCollector;
import com.internship.moviecrawler.model.Movie;
import com.internship.moviecrawler.repository.MovieRepository;
import com.internship.moviecrawler.repository.SqliteMovieRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Main orchestrator for the movie crawler.
 *
 * Flow: discover URLs via UrlCollector → check freshness → fetch → parse → upsert → backup → summary.
 * Each cycle auto-discovers new movie URLs from toivote.com seed pages.
 */
public class App {
    private static final Logger log = LoggerFactory.getLogger(App.class);
    private static final Logger failedUrlsLog = LoggerFactory.getLogger("FailedUrls");

    public static void main(String[] args) {
        Instant start = Instant.now();

        AppConfig config = new AppConfig();

        int total = 0, crawled = 0, inserted = 0, updated = 0, skipped = 0;
        int fetchFailed = 0, parseFailed = 0;

        MovieRepository repo = new SqliteMovieRepository(config.getDbPath());
        MovieFetcher fetcher = new MovieFetcher(
                config.getFetchConnectTimeoutMs(),
                config.getFetchRequestTimeoutMs(),
                config.getFetchMaxRetries(),
                config.getFetchUserAgent()
        );
        MovieParser parser = new MovieParser();
        DatabaseBackup backup = new DatabaseBackup(Path.of(config.getBackupDir()));

        try {
            // Auto-discover movie URLs from toivote.com seed pages
            Path urlsFile = Path.of(config.getDbPath()).getParent().resolve("urls.txt");
            Set<String> urlSet = UrlCollector.collect(fetcher, urlsFile);
            List<String> urls = new ArrayList<>(urlSet);
            total = urls.size();
            log.info("Starting crawl of {} URLs (auto-discovered from toivote.com)", total);

            Instant freshSince = Instant.now().minus(config.getFreshnessThresholdHours(), ChronoUnit.HOURS);

            for (String url : urls) {
                try {
                    // Skip if fresh (< 24h since last crawl)
                    Optional<Movie> existing = repo.findByUrl(url);
                    if (existing.isPresent() && existing.get().getLastCrawledAt() != null) {
                        try {
                            Instant lastCrawled = parseTimestamp(existing.get().getLastCrawledAt());
                            if (lastCrawled != null && lastCrawled.isAfter(freshSince)) {
                                skipped++;
                                continue;
                            }
                        } catch (Exception ignored) {
                            // If format is unexpected, just re-crawl
                        }
                    }

                    // Fetch
                    String html = fetcher.fetch(url);

                    // Parse
                    Movie movie = parser.parse(html, url);

                    // Store
                    boolean isInsert = repo.upsert(movie);
                    if (isInsert) inserted++;
                    else updated++;
                    crawled++;

                } catch (FetchException.PermanentFetchException e) {
                    log.error("Permanent error — {}", e.getMessage());
                    failedUrlsLog.error(url);
                    fetchFailed++;
                } catch (FetchException.TransientFetchException e) {
                    log.error("Transient error (retries exhausted) — {}", e.getMessage());
                    failedUrlsLog.error(url);
                    fetchFailed++;
                } catch (FetchException e) {
                    log.error("Fetch error — {}", e.getMessage());
                    failedUrlsLog.error(url);
                    fetchFailed++;
                } catch (ParseException e) {
                    log.warn("Parse failed — {}", e.getMessage());
                    parseFailed++;
                } catch (Exception e) {
                    log.error("Unexpected error processing {} — {}", url, e.getMessage(), e);
                } finally {
                    try {
                        Thread.sleep(config.getInterRequestDelayMs());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Crawl interrupted");
                        break;
                    }
                }
            }

        } catch (IOException e) {
            log.error("Failed to collect URLs from seed pages", e);
        } finally {
            repo.close();

            // Backup
            try {
                Path backupFile = backup.backup(Path.of(config.getDbPath()));
                log.info("Backup saved to {}", backupFile);
            } catch (IOException e) {
                log.error("Backup failed", e);
            }

            // Summary
            Duration elapsed = Duration.between(start, Instant.now());
            log.info("========== Crawl Summary ==========");
            log.info("Total URLs:     {}", total);
            log.info("Crawled:        {} (inserted: {}, updated: {})", crawled, inserted, updated);
            log.info("Skipped (fresh):{}", skipped);
            log.info("Fetch failed:   {}", fetchFailed);
            log.info("Parse failed:   {}", parseFailed);
            log.info("Elapsed:        {}m {}s", elapsed.toMinutes(), elapsed.toSecondsPart());
            log.info("====================================");
        }
    }

    /**
     * Parse SQLite datetime('now') timestamp: "2026-07-24 12:23:15"
     */
    private static Instant parseTimestamp(String ts) {
        if (ts == null || ts.isBlank()) return null;
        try {
            // SQLite format: "yyyy-MM-dd HH:mm:ss"
            String isoLike = ts.replace(" ", "T") + "Z";
            return Instant.parse(isoLike);
        } catch (Exception e) {
            return null;
        }
    }
}
