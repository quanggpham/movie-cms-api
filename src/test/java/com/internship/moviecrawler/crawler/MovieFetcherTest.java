package com.internship.moviecrawler.crawler;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.internship.moviecrawler.crawler.FetchException.PermanentFetchException;
import com.internship.moviecrawler.crawler.FetchException.TransientFetchException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MovieFetcherTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private final MovieFetcher fetcher = new MovieFetcher(
            2000,  // connect timeout
            3000,  // request timeout
            2,     // max retries (1 initial + 2 retry = 3 total)
            "MovieCrawlerTest/1.0"
    );

    // F1: 200 text/html → returns HTML
    @Test
    void fetch_200_ShouldReturnHtml() throws Exception {
        wireMock.stubFor(get(urlEqualTo("/movie"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=utf-8")
                        .withBody("<html><h1>Test Movie</h1></html>")));

        String html = fetcher.fetch(wireMock.url("/movie"));

        assertTrue(html.contains("Test Movie"));
    }

    // F2: 404 → PermanentFetchException, no retry
    @Test
    void fetch_404_ShouldThrowPermanentException() {
        wireMock.stubFor(get(urlEqualTo("/gone"))
                .willReturn(aResponse().withStatus(404)));

        PermanentFetchException ex = assertThrows(PermanentFetchException.class,
                () -> fetcher.fetch(wireMock.url("/gone")));
        assertTrue(ex.getMessage().contains("HTTP 404"));

        // Verify exactly 1 request (no retry)
        wireMock.verify(1, getRequestedFor(urlEqualTo("/gone")));
    }

    // F3: 500×2 then 200 → succeeds on 3rd attempt
    @Test
    void fetch_500Then200_ShouldSucceedAfterRetry() throws Exception {
        wireMock.stubFor(get(urlEqualTo("/flaky"))
                .inScenario("retry-then-success")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("first-retry"));

        wireMock.stubFor(get(urlEqualTo("/flaky"))
                .inScenario("retry-then-success")
                .whenScenarioStateIs("first-retry")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("second-retry"));

        wireMock.stubFor(get(urlEqualTo("/flaky"))
                .inScenario("retry-then-success")
                .whenScenarioStateIs("second-retry")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><h1>Finally</h1></html>")));

        String html = fetcher.fetch(wireMock.url("/flaky"));

        assertTrue(html.contains("Finally"));
        wireMock.verify(3, getRequestedFor(urlEqualTo("/flaky")));
    }

    // F4: 500×3 → TransientFetchException after all retries exhausted
    @Test
    void fetch_500x3_ShouldThrowTransientException() {
        wireMock.stubFor(get(urlEqualTo("/dead"))
                .willReturn(aResponse().withStatus(500)));

        TransientFetchException ex = assertThrows(TransientFetchException.class,
                () -> fetcher.fetch(wireMock.url("/dead")));
        assertTrue(ex.getMessage().contains("HTTP 500"));

        // 1 initial + 2 retries = 3 total
        wireMock.verify(3, getRequestedFor(urlEqualTo("/dead")));
    }

    // F5: 200 but non-text/html Content-Type → PermanentFetchException
    @Test
    void fetch_NonHtmlContentType_ShouldThrowPermanentException() {
        wireMock.stubFor(get(urlEqualTo("/json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"key\":\"value\"}")));

        PermanentFetchException ex = assertThrows(PermanentFetchException.class,
                () -> fetcher.fetch(wireMock.url("/json")));
        assertTrue(ex.getMessage().contains("Content-Type"));
    }
}
