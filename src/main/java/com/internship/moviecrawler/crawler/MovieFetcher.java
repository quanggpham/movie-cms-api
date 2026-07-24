package com.internship.moviecrawler.crawler;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import com.internship.moviecrawler.crawler.FetchException.PermanentFetchException;
import com.internship.moviecrawler.crawler.FetchException.TransientFetchException;

/**
 * Fetches raw HTML from a URL with retry logic.
 *
 * Responsibilities: HTTP request, retry, status code classification, Content-Type check.
 * Does NOT: parse HTML, know about Movie entity, manage inter-URL delay.
 */
public class MovieFetcher {

    private final HttpClient client;
    private final long requestTimeoutMs;
    private final int maxRetries;
    private final String userAgent;

    private static final Set<Integer> PERMANENT_STATUSES = Set.of(400, 403, 404);
    private static final Set<Integer> TRANSIENT_STATUSES = Set.of(408, 429);

    public MovieFetcher(long connectTimeoutMs, long requestTimeoutMs, int maxRetries, String userAgent) {
        this.requestTimeoutMs = requestTimeoutMs;
        this.maxRetries = maxRetries;
        this.userAgent = userAgent;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Fetch raw HTML from a URL.
     * 1 initial request + maxRetries retries = maxRetries+1 total attempts.
     * Backoff: 1s, 2s, 4s, ...
     *
     * @param url the URL to fetch
     * @return raw HTML body as String
     * @throws TransientFetchException if all retries exhausted on transient errors
     * @throws PermanentFetchException if a non-retryable error occurs
     * @throws FetchException if interrupted
     */
    public String fetch(String url) throws FetchException {
        int attempts = maxRetries + 1;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofMillis(requestTimeoutMs))
                        .header("User-Agent", userAgent)
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();

                if (status == 200) {
                    String contentType = response.headers().firstValue("Content-Type").orElse("");
                    if (!contentType.toLowerCase().contains("text/html")) {
                        throw new PermanentFetchException(url + " — unexpected Content-Type: " + contentType);
                    }
                    return response.body();
                }

                if (PERMANENT_STATUSES.contains(status)) {
                    throw new PermanentFetchException(url + " — HTTP " + status);
                }

                if (status == 429) {
                    Optional<String> retryAfter = response.headers().firstValue("Retry-After");
                    if (retryAfter.isPresent()) {
                        try {
                            long sec = Long.parseLong(retryAfter.get());
                            Thread.sleep(sec * 1000L);
                        } catch (NumberFormatException e) {
                            sleepBackoff(attempt);
                        }
                    } else {
                        sleepBackoff(attempt);
                    }
                    if (attempt >= attempts) {
                        throw new TransientFetchException(url + " — retries exhausted: HTTP 429");
                    }
                    continue;
                }

                if (TRANSIENT_STATUSES.contains(status) || status >= 500) {
                    sleepBackoff(attempt);
                    if (attempt >= attempts) {
                        throw new TransientFetchException(url + " — retries exhausted: HTTP " + status);
                    }
                    continue;
                }

                throw new PermanentFetchException(url + " — unexpected HTTP " + status);

            } catch (PermanentFetchException e) {
                throw e;
            } catch (TransientFetchException e) {
                if (attempt >= attempts) throw e;
            } catch (HttpTimeoutException e) {
                sleepBackoff(attempt);
                if (attempt >= attempts) {
                    throw new TransientFetchException(url + " — request timeout", e);
                }
            } catch (IOException e) {
                sleepBackoff(attempt);
                if (attempt >= attempts) {
                    throw new TransientFetchException(url + " — connection error", e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FetchException(url + " — interrupted", e);
            }
        }

        throw new TransientFetchException(url + " — all " + attempts + " attempts failed");
    }

    private void sleepBackoff(int attempt) {
        try {
            long ms = (long) Math.pow(2, attempt - 1) * 1000L; // 1s, 2s, 4s, ...
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
