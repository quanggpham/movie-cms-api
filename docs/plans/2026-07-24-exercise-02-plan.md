# Exercise 2: Movie Crawler — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a web crawler that fetches ~100 movie pages from toivote.com, extracts structured data, stores in SQLite with backup, packs as fat JAR, and deploys on Docker Linux VM with shell script running every 5 seconds.

**Architecture:** Layered with 5 packages — model (entity), crawler (fetch + parse), repository (SQLite), config (properties), backup (file copy). Fetcher and Parser have separate, single responsibilities. Repository uses interface to allow swap (SQLite → future mock/cache). Each exercise (2→6) gets its own commit; architecture designed for incremental extension.

**Tech Stack:** Java 17, Maven 3.8+, Jsoup 1.18.1, SQLite JDBC 3.46.1.0, Gson 2.11.0, Logback 1.5.6, JUnit 5.11.0, Docker (Ubuntu 22.04)

## Global Constraints

- Java 17 source/target
- Encoding UTF-8
- Package root: `com.internship.moviecrawler`
- One git commit per task
- Interface-first for repository (forward compatibility for ex3-6)
- TDD where applicable (parser, repository)

---

### Task 0: Explore toivote.com HTML Structure (Pre-requisite)

**Files:**
- Create: `src/test/resources/html/titanic.html` (downloaded sample page)
- Create: `src/test/resources/html/minimal.html` (hand-crafted minimal page)
- Create: `src/test/resources/html/malformed.html` (hand-crafted page with no title)

**Purpose:** Determine CSS selectors by analyzing real toivote.com pages before writing parser code. This task produces the test fixtures and selector map that Task 4 (MovieParser) depends on.

- [ ] **Step 1: Fetch 2-3 real movie pages from toivote.com**

Use WebFetch to retrieve actual HTML from a few toivote.com movie detail pages. Examine the DOM structure to identify CSS selectors for:
- Title: `<title>` or `<h1>`
- Release Year: element containing year
- Country: element containing country name
- Genres: elements listing genres
- Directors: elements listing directors
- Actors: elements listing actors

Save one representative page as `src/test/resources/html/titanic.html`.

- [ ] **Step 2: Create CSS selector config entries**

Document selectors in a comment block for Task 4's `MovieParser`. Example (adjust based on actual DOM):
```java
// Selector constants (determined from toivote.com HTML analysis)
// title:     h1.title-movie
// year:      span.year
// country:   span.country
// genres:    div.genres a
// directors: div.directors a
// actors:    div.actors a
```

- [ ] **Step 3: Create minimal test fixture**

Create `src/test/resources/html/minimal.html` — a hand-crafted page with only title + year, no genres/directors/actors elements:
```html
<!DOCTYPE html>
<html><head><title>Test Movie</title></head>
<body>
<h1>Test Movie</h1>
<span class="year">2023</span>
</body></html>
```

- [ ] **Step 4: Create malformed test fixture**

Create `src/test/resources/html/malformed.html` — a page with no `<title>` or `<h1>`:
```html
<!DOCTYPE html>
<html><head></head>
<body>
<div>No title element here</div>
</body></html>
```

- [ ] **Step 5: Commit**

```bash
git add src/test/resources/html/titanic.html src/test/resources/html/minimal.html src/test/resources/html/malformed.html
git commit -m "test(ex02): add HTML test fixtures for MovieParser"
```

---

### Task 1: Movie Entity + SQLite Schema Init

**Files:**
- Create: `src/main/java/com/internship/moviecrawler/model/Movie.java`
- Create: `src/main/java/com/internship/moviecrawler/repository/MovieRepository.java`
- Create: `src/main/java/com/internship/moviecrawler/repository/SqliteMovieRepository.java`

**Interfaces:**
- Produces: `Movie` (9 fields), `MovieRepository` interface (upsert, findAll, findById, existsByUrl, findByUrl), `SqliteMovieRepository` (implements repository + handles schema creation + JSON deserialization)

- [ ] **Step 1: Create Movie entity**

```java
package com.internship.moviecrawler.model;

import java.util.Collections;
import java.util.List;

public class Movie {
    private Long id;
    private String url;
    private String title;
    private Integer releaseYear;
    private String country;
    private List<String> genres;
    private List<String> directors;
    private List<String> actors;
    private String createdAt;
    private String updatedAt;
    private String lastCrawledAt;

    public Movie() {
        this.country = "";
        this.genres = Collections.emptyList();
        this.directors = Collections.emptyList();
        this.actors = Collections.emptyList();
    }

    public Movie(String url, String title, Integer releaseYear, String country,
                 List<String> genres, List<String> directors, List<String> actors) {
        this.url = url;
        this.title = title;
        this.releaseYear = releaseYear;
        this.country = country != null ? country : "";
        this.genres = genres != null ? genres : Collections.emptyList();
        this.directors = directors != null ? directors : Collections.emptyList();
        this.actors = actors != null ? actors : Collections.emptyList();
    }

    // Getters and setters for all fields (standard JavaBean pattern)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Integer getReleaseYear() { return releaseYear; }
    public void setReleaseYear(Integer releaseYear) { this.releaseYear = releaseYear; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public List<String> getGenres() { return genres; }
    public void setGenres(List<String> genres) { this.genres = genres != null ? genres : Collections.emptyList(); }

    public List<String> getDirectors() { return directors; }
    public void setDirectors(List<String> directors) { this.directors = directors != null ? directors : Collections.emptyList(); }

    public List<String> getActors() { return actors; }
    public void setActors(List<String> actors) { this.actors = actors != null ? actors : Collections.emptyList(); }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getLastCrawledAt() { return lastCrawledAt; }
    public void setLastCrawledAt(String lastCrawledAt) { this.lastCrawledAt = lastCrawledAt; }
}
```

- [ ] **Step 2: Create MovieRepository interface**

```java
package com.internship.moviecrawler.repository;

import com.internship.moviecrawler.model.Movie;
import java.util.List;
import java.util.Optional;

public interface MovieRepository {
    /**
     * Insert or update a movie. Uses url as conflict key.
     * @return true if a new row was inserted, false if an existing row was updated
     */
    boolean upsert(Movie movie);

    /** Returns all movies in the database */
    List<Movie> findAll();

    /** Find by primary key id */
    Optional<Movie> findById(long id);

    /** Check if a URL already exists in the database */
    boolean existsByUrl(String url);

    /** Find by URL (natural key), returns empty if not found */
    Optional<Movie> findByUrl(String url);

    /** Close database connection */
    void close();
}
```

- [ ] **Step 3: Create SqliteMovieRepository**

Create the schema init method and all CRUD operations. Key implementation details:
- Connection stored as field, created in constructor
- `CREATE TABLE IF NOT EXISTS` on init
- Upsert uses `ON CONFLICT(url) DO UPDATE SET ...`
- JSON columns (`genres`, `directors`, `actors`) serialized via Gson on write, deserialized on read
- Null-safe JSON deserialization: empty string or null → `Collections.emptyList()`
- SQLite file path: `data/movies.db` relative to working directory

```java
package com.internship.moviecrawler.repository;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.internship.moviecrawler.model.Movie;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.*;

public class SqliteMovieRepository implements MovieRepository {
    private static final Type LIST_STRING_TYPE = new TypeToken<List<String>>(){}.getType();

    private final Connection conn;
    private final Gson gson;

    private static final String CREATE_TABLE = """
        CREATE TABLE IF NOT EXISTS movies (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            url             TEXT NOT NULL UNIQUE,
            title           TEXT NOT NULL,
            release_year    INTEGER,
            country         TEXT DEFAULT '',
            genres          TEXT DEFAULT '[]',
            directors       TEXT DEFAULT '[]',
            actors          TEXT DEFAULT '[]',
            created_at      TEXT DEFAULT (datetime('now')),
            updated_at      TEXT DEFAULT (datetime('now')),
            last_crawled_at TEXT DEFAULT (datetime('now'))
        )
        """;

    private static final String UPSERT_SQL = """
        INSERT INTO movies (url, title, release_year, country, genres, directors, actors, last_crawled_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, datetime('now'))
        ON CONFLICT(url) DO UPDATE SET
            title           = excluded.title,
            release_year    = excluded.release_year,
            country         = excluded.country,
            genres          = excluded.genres,
            directors       = excluded.directors,
            actors          = excluded.actors,
            updated_at      = datetime('now'),
            last_crawled_at = datetime('now')
        """;

    private static final String SELECT_ALL = "SELECT * FROM movies";
    private static final String SELECT_BY_ID = "SELECT * FROM movies WHERE id = ?";
    private static final String SELECT_BY_URL = "SELECT * FROM movies WHERE url = ?";
    private static final String EXISTS_BY_URL = "SELECT COUNT(*) FROM movies WHERE url = ?";

    public SqliteMovieRepository(String dbPath) {
        this.gson = new Gson();
        try {
            Class.forName("org.sqlite.JDBC");
            this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            initSchema();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize SQLite database", e);
        }
    }

    private void initSchema() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_TABLE);
        }
    }

    @Override
    public boolean upsert(Movie movie) {
        try (PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {
            // Check if URL already exists to determine insert vs update
            boolean exists = existsByUrl(movie.getUrl());

            ps.setString(1, movie.getUrl());
            ps.setString(2, movie.getTitle());
            if (movie.getReleaseYear() != null) {
                ps.setInt(3, movie.getReleaseYear());
            } else {
                ps.setNull(3, Types.INTEGER);
            }
            ps.setString(4, movie.getCountry());
            ps.setString(5, gson.toJson(movie.getGenres()));
            ps.setString(6, gson.toJson(movie.getDirectors()));
            ps.setString(7, gson.toJson(movie.getActors()));
            ps.executeUpdate();

            return !exists; // true = new insert, false = update
        } catch (SQLException e) {
            throw new RuntimeException("Failed to upsert movie: " + movie.getUrl(), e);
        }
    }

    @Override
    public List<Movie> findAll() {
        List<Movie> movies = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_ALL)) {
            while (rs.next()) {
                movies.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch all movies", e);
        }
        return movies;
    }

    @Override
    public Optional<Movie> findById(long id) {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find movie by id: " + id, e);
        }
        return Optional.empty();
    }

    @Override
    public boolean existsByUrl(String url) {
        try (PreparedStatement ps = conn.prepareStatement(EXISTS_BY_URL)) {
            ps.setString(1, url);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check URL existence: " + url, e);
        }
    }

    @Override
    public Optional<Movie> findByUrl(String url) {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_BY_URL)) {
            ps.setString(1, url);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find movie by url: " + url, e);
        }
        return Optional.empty();
    }

    @Override
    public void close() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to close database connection", e);
        }
    }

    private Movie mapRow(ResultSet rs) throws SQLException {
        Movie m = new Movie();
        m.setId(rs.getLong("id"));
        m.setUrl(rs.getString("url"));
        m.setTitle(rs.getString("title"));
        m.setReleaseYear(rs.getObject("release_year") != null ? rs.getInt("release_year") : null);
        m.setCountry(rs.getString("country"));
        m.setGenres(deserializeList(rs.getString("genres")));
        m.setDirectors(deserializeList(rs.getString("directors")));
        m.setActors(deserializeList(rs.getString("actors")));
        m.setCreatedAt(rs.getString("created_at"));
        m.setUpdatedAt(rs.getString("updated_at"));
        m.setLastCrawledAt(rs.getString("last_crawled_at"));
        return m;
    }

    private List<String> deserializeList(String json) {
        if (json == null || json.isEmpty() || "null".equals(json)) {
            return Collections.emptyList();
        }
        return gson.fromJson(json, LIST_STRING_TYPE);
    }
}
```

- [ ] **Step 4: Update pom.xml — fix mainClass**

The current pom.xml has `<mainClass>com.internship.App</mainClass>` which doesn't match the new package structure. Update it:

In `pom.xml`, change line 93:
```xml
<mainClass>com.internship.moviecrawler.App</mainClass>
```

- [ ] **Step 5: Verify Maven compiles**

Run: `mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/internship/moviecrawler/model/Movie.java \
        src/main/java/com/internship/moviecrawler/repository/MovieRepository.java \
        src/main/java/com/internship/moviecrawler/repository/SqliteMovieRepository.java \
        pom.xml
git commit -m "feat(ex02): add Movie entity + SQLite repository"
```

---

### Task 2: SqliteMovieRepository Tests (TDD verification)

**Files:**
- Create: `src/test/java/com/internship/moviecrawler/repository/SqliteMovieRepositoryTest.java`

**Interfaces:**
- Consumes: `SqliteMovieRepository`, `Movie`
- Produces: 8 integration tests verifying repository contract

- [ ] **Step 1: Write all 8 repository tests**

```java
package com.internship.moviecrawler.repository;

import com.internship.moviecrawler.model.Movie;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SqliteMovieRepositoryTest {

    private SqliteMovieRepository repo;

    @BeforeEach
    void setUp() {
        repo = new SqliteMovieRepository(":memory:");
    }

    @AfterEach
    void tearDown() {
        repo.close();
    }

    // R1: upsert() — insert new
    @Test
    void upsert_NewMovie_ShouldInsert() {
        Movie movie = new Movie("http://example.com/a", "Film A", 2023, "Vietnam",
                List.of("Hành động", "Tình cảm"), List.of("Dir A"), List.of("Actor A"));

        boolean inserted = repo.upsert(movie);

        assertTrue(inserted, "First upsert should insert");
        Optional<Movie> found = repo.findByUrl("http://example.com/a");
        assertTrue(found.isPresent());
        assertEquals("Film A", found.get().getTitle());
        assertEquals(2023, found.get().getReleaseYear());
        assertEquals(List.of("Hành động", "Tình cảm"), found.get().getGenres());
        assertEquals(List.of("Dir A"), found.get().getDirectors());
        assertEquals(List.of("Actor A"), found.get().getActors());
        assertNotNull(found.get().getCreatedAt());
        assertNotNull(found.get().getUpdatedAt());
    }

    // R2: upsert() — update existing (id and created_at preserved)
    @Test
    void upsert_ExistingUrl_ShouldUpdatePreservingId() {
        Movie first = new Movie("http://example.com/b", "Film B", 2022, "USA",
                List.of("Drama"), List.of(), List.of());
        repo.upsert(first);
        Movie firstSaved = repo.findByUrl("http://example.com/b").orElseThrow();
        Long originalId = firstSaved.getId();
        String originalCreatedAt = firstSaved.getCreatedAt();

        Movie updated = new Movie("http://example.com/b", "Film B v2", 2022, "USA",
                List.of("Drama", "Action"), List.of("Dir B"), List.of("Actor B"));

        boolean inserted = repo.upsert(updated);

        assertFalse(inserted, "Second upsert should update (not insert)");
        Movie updatedSaved = repo.findByUrl("http://example.com/b").orElseThrow();
        assertEquals(originalId, updatedSaved.getId(), "id must be preserved");
        assertEquals(originalCreatedAt, updatedSaved.getCreatedAt(), "created_at must be preserved");
        assertEquals("Film B v2", updatedSaved.getTitle());
        assertEquals(List.of("Drama", "Action"), updatedSaved.getGenres());
        assertNotEquals(updatedSaved.getCreatedAt(), updatedSaved.getUpdatedAt(),
                "updated_at should differ from created_at after update");
    }

    // R3: upsert() — isolation (other rows unaffected)
    @Test
    void upsert_ShouldNotAffectOtherRows() {
        Movie a = new Movie("http://example.com/a", "Film A", 2020, "VN",
                List.of(), List.of(), List.of());
        Movie b = new Movie("http://example.com/b", "Film B", 2021, "US",
                List.of(), List.of(), List.of());
        repo.upsert(a);
        repo.upsert(b);

        Movie aUpdated = new Movie("http://example.com/a", "Film A Revised", 2020, "VN",
                List.of("Comedy"), List.of(), List.of());
        repo.upsert(aUpdated);

        Optional<Movie> bUnchanged = repo.findByUrl("http://example.com/b");
        assertTrue(bUnchanged.isPresent());
        assertEquals("Film B", bUnchanged.get().getTitle(), "Unrelated row must be untouched");
    }

    // R4: existsByUrl() — found
    @Test
    void existsByUrl_Existing_ShouldReturnTrue() {
        repo.upsert(new Movie("http://example.com/x", "X", null, "",
                List.of(), List.of(), List.of()));
        assertTrue(repo.existsByUrl("http://example.com/x"));
    }

    // R5: existsByUrl() — not found
    @Test
    void existsByUrl_NonExisting_ShouldReturnFalse() {
        assertFalse(repo.existsByUrl("http://example.com/nonexistent"));
    }

    // R6: findAll()
    @Test
    void findAll_ShouldReturnAllMovies() {
        repo.upsert(new Movie("http://example.com/a", "A", null, "",
                List.of(), List.of(), List.of()));
        repo.upsert(new Movie("http://example.com/b", "B", null, "",
                List.of(), List.of(), List.of()));
        repo.upsert(new Movie("http://example.com/c", "C", null, "",
                List.of(), List.of(), List.of()));

        List<Movie> all = repo.findAll();
        assertEquals(3, all.size());
    }

    // R7: JSON deserialization
    @Test
    void findById_ShouldDeserializeJsonLists() {
        Movie movie = new Movie("http://example.com/list", "List Test", 2023, "Korea",
                List.of("A", "B", "C"), List.of("D1", "D2"), List.of("Actor1"));
        repo.upsert(movie);

        Movie loaded = repo.findByUrl("http://example.com/list").orElseThrow();
        assertEquals(List.of("A", "B", "C"), loaded.getGenres());
        assertEquals(List.of("D1", "D2"), loaded.getDirectors());
        assertEquals(List.of("Actor1"), loaded.getActors());
    }

    // R8: Null handling in JSON columns
    @Test
    void upsert_EmptyLists_ShouldReturnEmptyLists() {
        Movie movie = new Movie("http://example.com/empty", "Empty Lists", 2023, "",
                List.of(), List.of(), List.of());
        repo.upsert(movie);

        Movie loaded = repo.findByUrl("http://example.com/empty").orElseThrow();
        assertNotNull(loaded.getGenres());
        assertNotNull(loaded.getDirectors());
        assertNotNull(loaded.getActors());
        assertTrue(loaded.getGenres().isEmpty());
        assertTrue(loaded.getDirectors().isEmpty());
        assertTrue(loaded.getActors().isEmpty());
    }
}
```

- [ ] **Step 2: Run tests**

Run: `mvn test`
Expected: All 8 tests PASS

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/internship/moviecrawler/repository/SqliteMovieRepositoryTest.java
git commit -m "test(ex02): add SqliteMovieRepository tests — 8 cases"
```

---

### Task 3: MovieFetcher — HTTP fetch with retry

**Files:**
- Create: `src/main/java/com/internship/moviecrawler/crawler/FetchException.java`
- Create: `src/main/java/com/internship/moviecrawler/crawler/MovieFetcher.java`

**Interfaces:**
- Consumes: nothing (standalone component)
- Produces: `FetchException` (abstract, with `TransientFetchException` and `PermanentFetchException` subclasses), `MovieFetcher(String connectTimeout, String requestTimeout, int maxRetries, String userAgent)`, `MovieFetcher.fetch(String url)` → `String` (raw HTML)

- [ ] **Step 1: Create FetchException hierarchy**

```java
package com.internship.moviecrawler.crawler;

public class FetchException extends Exception {
    public FetchException(String message) {
        super(message);
    }

    public FetchException(String message, Throwable cause) {
        super(message, cause);
    }

    public static class TransientFetchException extends FetchException {
        public TransientFetchException(String message) {
            super(message);
        }

        public TransientFetchException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class PermanentFetchException extends FetchException {
        public PermanentFetchException(String message) {
            super(message);
        }

        public PermanentFetchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
```

- [ ] **Step 2: Create MovieFetcher**

```java
package com.internship.moviecrawler.crawler;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Optional;

public class MovieFetcher {
    private final HttpClient client;
    private final int maxRetries;
    private final String userAgent;

    private static final java.util.Set<Integer> PERMANENT_STATUSES =
            java.util.Set.of(400, 403, 404);
    private static final java.util.Set<Integer> TRANSIENT_STATUSES =
            java.util.Set.of(408, 429);

    public MovieFetcher(long connectTimeoutMs, long requestTimeoutMs, int maxRetries, String userAgent) {
        this.maxRetries = maxRetries;
        this.userAgent = userAgent;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Fetch raw HTML from a URL with retry logic.
     * 1 initial request + maxRetries retries = maxRetries+1 total attempts.
     *
     * @param url the URL to fetch
     * @return raw HTML body as String
     * @throws FetchException.TransientFetchException if all retries exhausted on transient errors
     * @throws FetchException.PermanentFetchException if a non-retryable error occurs
     */
    public String fetch(String url) throws FetchException {
        int attempts = maxRetries + 1;
        int attempt = 0;

        while (attempt < attempts) {
            attempt++;
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
                        throw new FetchException.PermanentFetchException(
                                "Unexpected Content-Type: " + contentType + " for URL: " + url);
                    }
                    return response.body();
                }

                if (PERMANENT_STATUSES.contains(status)) {
                    throw new FetchException.PermanentFetchException(
                            "HTTP " + status + " for URL: " + url);
                }

                if (TRANSIENT_STATUSES.contains(status)) {
                    if (status == 429) {
                        Optional<String> retryAfter = response.headers().firstValue("Retry-After");
                        if (retryAfter.isPresent()) {
                            try {
                                long seconds = Long.parseLong(retryAfter.get());
                                Thread.sleep(seconds * 1000L);
                            } catch (NumberFormatException e) {
                                sleepBackoff(attempt);
                            }
                        } else {
                            sleepBackoff(attempt);
                        }
                    } else {
                        sleepBackoff(attempt);
                    }
                    if (attempt >= attempts) {
                        throw new FetchException.TransientFetchException(
                                "Exhausted retries after HTTP " + status + " for URL: " + url);
                    }
                    continue;
                }

                // 5xx
                if (status >= 500) {
                    sleepBackoff(attempt);
                    if (attempt >= attempts) {
                        throw new FetchException.TransientFetchException(
                                "Exhausted retries after HTTP " + status + " for URL: " + url);
                    }
                    continue;
                }

                throw new FetchException.PermanentFetchException(
                        "Unexpected HTTP " + status + " for URL: " + url);

            } catch (FetchException.PermanentFetchException e) {
                throw e;
            } catch (FetchException.TransientFetchException e) {
                throw e;
            } catch (HttpTimeoutException e) {
                sleepBackoff(attempt);
                if (attempt >= attempts) {
                    throw new FetchException.TransientFetchException(
                            "Request timeout for URL: " + url, e);
                }
            } catch (IOException e) {
                sleepBackoff(attempt);
                if (attempt >= attempts) {
                    throw new FetchException.TransientFetchException(
                            "Connection error for URL: " + url, e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FetchException(
                        "Fetch interrupted for URL: " + url, e);
            }
        }

        throw new FetchException.TransientFetchException(
                "Failed to fetch URL after " + attempts + " attempts: " + url);
    }

    private void sleepBackoff(int attempt) {
        try {
            long backoffMs = (long) Math.pow(2, attempt - 1) * 1000L; // 1s, 2s, 4s
            Thread.sleep(backoffMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Package-private for testing
    long getRequestTimeoutMs() {
        return requestTimeoutMs;
    }
}
```

Wait — `requestTimeoutMs` is used inside the lambda but not stored as a field. Fix: store it.

Replace the `MovieFetcher` class body with this corrected version:

```java
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
                        throw new FetchException.PermanentFetchException(
                                "Unexpected Content-Type: " + contentType);
                    }
                    return response.body();
                }

                if (PERMANENT_STATUSES.contains(status)) {
                    throw new FetchException.PermanentFetchException("HTTP " + status);
                }

                if (status == 429) {
                    Optional<String> retryAfter = response.headers().firstValue("Retry-After");
                    if (retryAfter.isPresent()) {
                        try {
                            long sec = Long.parseLong(retryAfter.get());
                            Thread.sleep(sec * 1000L);
                        } catch (NumberFormatException numFmt) {
                            sleepBackoff(attempt);
                        }
                    } else {
                        sleepBackoff(attempt);
                    }
                    if (attempt >= attempts) {
                        throw new FetchException.TransientFetchException("Retries exhausted: HTTP 429");
                    }
                    continue;
                }

                if (TRANSIENT_STATUSES.contains(status) || status >= 500) {
                    sleepBackoff(attempt);
                    if (attempt >= attempts) {
                        throw new FetchException.TransientFetchException("Retries exhausted: HTTP " + status);
                    }
                    continue;
                }

                throw new FetchException.PermanentFetchException("Unexpected HTTP " + status);

            } catch (FetchException.PermanentFetchException e) {
                throw new FetchException.PermanentFetchException(url + " — " + e.getMessage(), e.getCause());
            } catch (FetchException.TransientFetchException e) {
                if (attempt >= attempts) {
                    throw new FetchException.TransientFetchException(url + " — " + e.getMessage(), e.getCause());
                }
            } catch (HttpTimeoutException e) {
                sleepBackoff(attempt);
                if (attempt >= attempts) {
                    throw new FetchException.TransientFetchException(url + " — timeout", e);
                }
            } catch (IOException e) {
                sleepBackoff(attempt);
                if (attempt >= attempts) {
                    throw new FetchException.TransientFetchException(url + " — connection error", e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FetchException(url + " — interrupted", e);
            }
        }

        throw new FetchException.TransientFetchException(url + " — all " + attempts + " attempts failed");
    }

    private void sleepBackoff(int attempt) {
        try {
            long ms = (long) Math.pow(2, attempt - 1) * 1000L;
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

- [ ] **Step 3: Verify Maven compiles**

Run: `mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/internship/moviecrawler/crawler/FetchException.java \
        src/main/java/com/internship/moviecrawler/crawler/MovieFetcher.java
git commit -m "feat(ex02): add MovieFetcher with retry + error classification"
```

---

### Task 4: MovieParser — CSS selector parser + tests

**Files:**
- Create: `src/main/java/com/internship/moviecrawler/crawler/ParseException.java`
- Create: `src/main/java/com/internship/moviecrawler/crawler/MovieParser.java`
- Create: `src/test/java/com/internship/moviecrawler/crawler/MovieParserTest.java`

**Interfaces:**
- Consumes: `Movie` entity, HTML test fixtures from Task 0
- Produces: `ParseException`, `MovieParser` (stateless, single method `parse(String html, String url)` → `Movie`)

**CRITICAL PREREQUISITE:** CSS selectors in this task are placeholders. The actual selectors depend on Task 0's analysis of toivote.com HTML. When implementing, replace the placeholder selectors with real ones discovered in Task 0.

- [ ] **Step 1: Create ParseException**

```java
package com.internship.moviecrawler.crawler;

public class ParseException extends Exception {
    public ParseException(String message) {
        super(message);
    }

    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 2: Create MovieParser**

```java
package com.internship.moviecrawler.crawler;

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

public class MovieParser {

    // === CSS SELECTORS (replace with real selectors from Task 0) ===
    private static final String SEL_TITLE = "h1";           // PLACEHOLDER: actual selector from toivote.com
    private static final String SEL_YEAR = "span.year";     // PLACEHOLDER
    private static final String SEL_COUNTRY = "span.country"; // PLACEHOLDER
    private static final String SEL_GENRES = "div.genres a";  // PLACEHOLDER
    private static final String SEL_DIRECTORS = "div.directors a"; // PLACEHOLDER
    private static final String SEL_ACTORS = "div.actors a";  // PLACEHOLDER

    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19|20)\\d{2}\\b");

    /**
     * Parse raw HTML into a Movie object.
     *
     * @param html raw HTML string
     * @param url  the source URL (stored in Movie for dedup)
     * @return populated Movie
     * @throws ParseException if title cannot be extracted from HTML
     */
    public Movie parse(String html, String url) throws ParseException {
        if (html == null || html.isBlank()) {
            throw new ParseException("HTML is null or empty for URL: " + url);
        }

        Document doc = Jsoup.parse(html);

        // Title — REQUIRED, fail if missing
        String title = extractTitle(doc, url);

        // Optional fields
        Integer releaseYear = extractYear(doc);
        String country = extractCountry(doc);
        List<String> genres = extractList(doc, SEL_GENRES);
        List<String> directors = extractList(doc, SEL_DIRECTORS);
        List<String> actors = extractList(doc, SEL_ACTORS);

        return new Movie(url, title, releaseYear, country, genres, directors, actors);
    }

    private String extractTitle(Document doc, String url) throws ParseException {
        // Try <title> first
        String title = doc.title();
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        // Try h1
        Elements h1s = doc.select("h1");
        if (!h1s.isEmpty()) {
            String h1Text = h1s.first().text();
            if (!h1Text.isBlank()) {
                return h1Text.trim();
            }
        }
        // Try custom selector
        Elements titles = doc.select(SEL_TITLE);
        if (!titles.isEmpty()) {
            String selText = titles.first().text();
            if (!selText.isBlank()) {
                return selText.trim();
            }
        }
        throw new ParseException("No title found for URL: " + url);
    }

    private Integer extractYear(Document doc) {
        Elements elements = doc.select(SEL_YEAR);
        if (!elements.isEmpty()) {
            String text = elements.first().text();
            Matcher m = YEAR_PATTERN.matcher(text);
            if (m.find()) {
                return Integer.parseInt(m.group());
            }
        }
        // Fallback: search entire body text for a year pattern
        String bodyText = doc.body().text();
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
        if (elements.isEmpty()) {
            return Collections.emptyList();
        }
        return elements.stream()
                .map(Element::text)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
```

- [ ] **Step 3: Write all 8 parser tests**

```java
package com.internship.moviecrawler.crawler;

import com.internship.moviecrawler.model.Movie;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MovieParserTest {

    private final MovieParser parser = new MovieParser();

    // P1: Parse with all 6 fields
    @Test
    void parse_FullHtml_ShouldExtractAllFields() throws Exception {
        String html = Files.readString(Path.of("src/test/resources/html/titanic.html"));
        Movie movie = parser.parse(html, "http://example.com/movie");

        assertNotNull(movie.getTitle());
        assertFalse(movie.getTitle().isBlank());
        // NOTE: following assertions depend on actual titanic.html content from Task 0
        // Adjust expected values after Task 0 analysis
    }

    // P2: Minimal HTML — only title + year
    @Test
    void parse_MinimalHtml_ShouldHaveDefaults() throws Exception {
        String html = Files.readString(Path.of("src/test/resources/html/minimal.html"));
        Movie movie = parser.parse(html, "http://example.com/minimal");

        assertEquals("Test Movie", movie.getTitle());
        assertEquals(2023, movie.getReleaseYear());
        assertEquals("", movie.getCountry());
        assertTrue(movie.getGenres().isEmpty());
        assertTrue(movie.getDirectors().isEmpty());
        assertTrue(movie.getActors().isEmpty());
    }

    // P3: No title → ParseException
    @Test
    void parse_NoTitle_ShouldThrowParseException() throws Exception {
        String html = Files.readString(Path.of("src/test/resources/html/malformed.html"));
        assertThrows(ParseException.class, () -> parser.parse(html, "http://example.com/malformed"));
    }

    // P4: Null/empty HTML
    @Test
    void parse_NullHtml_ShouldThrowParseException() {
        assertThrows(ParseException.class, () -> parser.parse(null, "http://example.com/null"));
    }

    @Test
    void parse_EmptyHtml_ShouldThrowParseException() {
        assertThrows(ParseException.class, () -> parser.parse("   ", "http://example.com/blank"));
    }

    // P5: Year with mixed text
    @Test
    void parse_YearWithText_ShouldExtractNumber() throws Exception {
        String html = """
            <html><head><title>Test Film</title></head><body>
            <span class="year">Năm 2023 (tái bản)</span>
            </body></html>
            """;
        Movie movie = parser.parse(html, "http://example.com/yeartest");
        assertEquals(2023, movie.getReleaseYear());
    }

    // P6: Comma-separated genres
    @Test
    void parse_CommaSeparatedGenres_ShouldSplitAndTrim() throws Exception {
        String html = """
            <html><head><title>Genre Test</title></head><body>
            <div class="genres"><a>Hành động</a><a>Tình cảm</a><a>Hài</a></div>
            </body></html>
            """;
        Movie movie = parser.parse(html, "http://example.com/genretest");
        assertEquals(3, movie.getGenres().size());
        assertEquals("Hành động", movie.getGenres().get(0));
        assertEquals("Tình cảm", movie.getGenres().get(1));
        assertEquals("Hài", movie.getGenres().get(2));
    }

    // P7: Single director
    @Test
    void parse_SingleDirector_ShouldReturnListOfOne() throws Exception {
        String html = """
            <html><head><title>Director Test</title></head><body>
            <div class="directors"><a>James Cameron</a></div>
            </body></html>
            """;
        Movie movie = parser.parse(html, "http://example.com/dirtest");
        assertEquals(1, movie.getDirectors().size());
        assertEquals("James Cameron", movie.getDirectors().get(0));
    }

    // P8: Non-UTF-8 encoding
    @Test
    void parse_Iso8859Html_ShouldParseCorrectly() throws Exception {
        String html = """
            <!DOCTYPE html><html><head>
            <meta charset="ISO-8859-1">
            <title>Café & Cinéma</title></head><body>
            <span class="year">2022</span>
            </body></html>
            """;
        Movie movie = parser.parse(html, "http://example.com/encoding");
        assertEquals("Café & Cinéma", movie.getTitle());
        assertEquals(2022, movie.getReleaseYear());
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn test -Dtest=MovieParserTest`
Expected: All 8 tests PASS (P1 may need adjustment after actual titanic.html is loaded in Task 0)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/internship/moviecrawler/crawler/ParseException.java \
        src/main/java/com/internship/moviecrawler/crawler/MovieParser.java \
        src/test/java/com/internship/moviecrawler/crawler/MovieParserTest.java
git commit -m "feat(ex02): add MovieParser + 8 unit tests"
```

---

### Task 5: AppConfig — Configuration loader

**Files:**
- Create: `src/main/java/com/internship/moviecrawler/config/AppConfig.java`
- Create: `src/main/resources/config.properties`

**Interfaces:**
- Consumes: nothing (reads classpath properties)
- Produces: `AppConfig` singleton with typed getters for all config values

- [ ] **Step 1: Create config.properties**

```properties
# Movie Crawler Configuration
# Fetch settings
fetch.connect.timeout.ms=5000
fetch.request.timeout.ms=10000
fetch.max.retries=3
fetch.user.agent=Mozilla/5.0 (compatible; MovieCrawler/1.0)

# Crawl settings
crawl.inter.request.delay.ms=1500
crawl.freshness.threshold.hours=24

# Database
db.path=data/movies.db

# Backup
backup.dir=backup

# URL source
urls.file=urls.txt
```

- [ ] **Step 2: Create AppConfig**

```java
package com.internship.moviecrawler.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AppConfig {
    private static final String CONFIG_FILE = "config.properties";

    private final Properties props;

    public AppConfig() {
        this.props = new Properties();
        try (InputStream is = AppConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (is == null) {
                throw new RuntimeException("Config file not found: " + CONFIG_FILE);
            }
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config: " + CONFIG_FILE, e);
        }
    }

    // Fetch config
    public long getFetchConnectTimeoutMs() { return Long.parseLong(props.getProperty("fetch.connect.timeout.ms", "5000")); }
    public long getFetchRequestTimeoutMs() { return Long.parseLong(props.getProperty("fetch.request.timeout.ms", "10000")); }
    public int getFetchMaxRetries() { return Integer.parseInt(props.getProperty("fetch.max.retries", "3")); }
    public String getFetchUserAgent() { return props.getProperty("fetch.user.agent", "MovieCrawler/1.0"); }

    // Crawl config
    public long getInterRequestDelayMs() { return Long.parseLong(props.getProperty("crawl.inter.request.delay.ms", "1500")); }
    public int getFreshnessThresholdHours() { return Integer.parseInt(props.getProperty("crawl.freshness.threshold.hours", "24")); }

    // Paths
    public String getDbPath() { return props.getProperty("db.path", "data/movies.db"); }
    public String getBackupDir() { return props.getProperty("backup.dir", "backup"); }
    public String getUrlsFile() { return props.getProperty("urls.file", "urls.txt"); }
}
```

- [ ] **Step 3: Verify Maven compiles**

Run: `mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/internship/moviecrawler/config/AppConfig.java \
        src/main/resources/config.properties
git commit -m "feat(ex02): add AppConfig + config.properties"
```

---

### Task 6: DatabaseBackup — Copy .db file after close

**Files:**
- Create: `src/main/java/com/internship/moviecrawler/backup/DatabaseBackup.java`

**Interfaces:**
- Consumes: `AppConfig`
- Produces: `DatabaseBackup.backup(Path dbPath)` → copies to `backup/movies_YYYYMMDD_HHmmss.db`

- [ ] **Step 1: Create DatabaseBackup**

```java
package com.internship.moviecrawler.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DatabaseBackup {
    private final Path backupDir;

    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public DatabaseBackup(Path backupDir) {
        this.backupDir = backupDir;
    }

    /**
     * Copy the database file to the backup directory with a timestamped filename.
     * @param dbPath path to the active database file
     * @return path to the created backup file
     * @throws IOException if copy fails
     */
    public Path backup(Path dbPath) throws IOException {
        Files.createDirectories(backupDir);

        String timestamp = LocalDateTime.now().format(TS_FORMAT);
        String filename = "movies_" + timestamp + ".db";
        Path backupFile = backupDir.resolve(filename);

        Files.copy(dbPath, backupFile, StandardCopyOption.REPLACE_EXISTING);
        return backupFile;
    }
}
```

- [ ] **Step 2: Verify Maven compiles**

Run: `mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/internship/moviecrawler/backup/DatabaseBackup.java
git commit -m "feat(ex02): add DatabaseBackup"
```

---

### Task 7: App.java — Main orchestrator

**Files:**
- Create: `src/main/java/com/internship/moviecrawler/App.java`
- Create: `src/main/resources/urls.txt` (placeholder — real URLs entered by user)
- Create: `src/main/resources/logback.xml`

**Interfaces:**
- Consumes: All previous components (`AppConfig`, `MovieFetcher`, `MovieParser`, `SqliteMovieRepository`, `DatabaseBackup`, `Movie`)
- Produces: Executable main class that runs the full crawl pipeline

- [ ] **Step 1: Create logback.xml**

```xml
<configuration>
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/crawler.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/crawler.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{HH:mm:ss} [%thread] %-5level %logger{36} — %msg%n</pattern>
        </encoder>
    </appender>

    <appender name="FAILED_URLS" class="ch.qos.logback.core.FileAppender">
        <file>logs/failed-urls.log</file>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} %msg%n</pattern>
        </encoder>
    </appender>

    <logger name="FailedUrls" level="ERROR" additivity="false">
        <appender-ref ref="FAILED_URLS"/>
    </logger>

    <root level="INFO">
        <appender-ref ref="FILE"/>
    </root>
</configuration>
```

- [ ] **Step 2: Create App.java**

```java
package com.internship.moviecrawler;

import com.internship.moviecrawler.backup.DatabaseBackup;
import com.internship.moviecrawler.config.AppConfig;
import com.internship.moviecrawler.crawler.FetchException;
import com.internship.moviecrawler.crawler.MovieFetcher;
import com.internship.moviecrawler.crawler.MovieParser;
import com.internship.moviecrawler.crawler.ParseException;
import com.internship.moviecrawler.model.Movie;
import com.internship.moviecrawler.repository.MovieRepository;
import com.internship.moviecrawler.repository.SqliteMovieRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class App {
    private static final Logger log = LoggerFactory.getLogger(App.class);
    private static final Logger failedUrlsLog = LoggerFactory.getLogger("FailedUrls");

    public static void main(String[] args) {
        Instant start = Instant.now();

        AppConfig config = new AppConfig();

        // Counters
        int total = 0, crawled = 0, inserted = 0, updated = 0, skipped = 0;
        int fetchFailed = 0, parseFailed = 0;

        // Initialize components
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
            // Load URLs from classpath
            List<String> urls = loadUrls(config.getUrlsFile());
            total = urls.size();
            log.info("Starting crawl of {} URLs", total);

            Instant freshSince = Instant.now().minus(config.getFreshnessThresholdHours(), ChronoUnit.HOURS);

            for (String url : urls) {
                try {
                    // Skip if fresh (< 24h since last crawl)
                    Optional<Movie> existing = repo.findByUrl(url);
                    if (existing.isPresent() && existing.get().getLastCrawledAt() != null) {
                        try {
                            Instant lastCrawled = Instant.parse(existing.get().getLastCrawledAt()
                                    .replace(" ", "T") + "Z");
                            if (lastCrawled.isAfter(freshSince)) {
                                skipped++;
                                continue;
                            }
                        } catch (Exception ignored) {
                            // If lastCrawledAt format is unexpected, re-crawl
                        }
                    }

                    // Fetch
                    String html = fetcher.fetch(url);

                    // Parse
                    Movie movie = parser.parse(html, url);

                    // Store
                    boolean isInsert = repo.upsert(movie);
                    if (isInsert) {
                        inserted++;
                    } else {
                        updated++;
                    }
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
                    // Inter-request delay (always, even on skip/failure)
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
            log.error("Failed to read URLs file: {}", config.getUrlsFile(), e);
        } finally {
            // Close DB before backup
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

    private static List<String> loadUrls(String resourceName) throws IOException {
        List<String> urls = new ArrayList<>();
        InputStream is = App.class.getClassLoader().getResourceAsStream(resourceName);
        if (is == null) {
            throw new IOException("Resource not found: " + resourceName);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                urls.add(line);
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(urls)); // deduplicate, preserve order
    }
}
```

- [ ] **Step 3: Create placeholder urls.txt**

```text
# Movie URLs for toivote.com crawler
# One URL per line. Lines starting with # are comments.
# Add ~100 movie detail URLs below.
#
# Example format:
# https://toivote.com/movie/some-movie-slug
```

User will fill in 100 real URLs before running.

- [ ] **Step 4: Verify Maven compiles**

Run: `mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Run all tests**

Run: `mvn test`
Expected: All 16 tests PASS (8 repository + 8 parser)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/internship/moviecrawler/App.java \
        src/main/resources/config.properties \
        src/main/resources/urls.txt \
        src/main/resources/logback.xml
git commit -m "feat(ex02): add App orchestrator + logging + urls.txt"
```

---

### Task 8: Docker & Deploy setup

**Files:**
- Create: `docker/Dockerfile`
- Create: `scripts/run.sh`
- Modify: `.gitignore`

**Interfaces:**
- Consumes: Fat JAR from `mvn package`
- Produces: Docker image with SSH key auth, shell script loop

- [ ] **Step 1: Update .gitignore**

Add runtime and sensitive paths to `.gitignore`:
```
# Runtime data
data/
backup/
logs/

# SSH keys
docker/authorized_keys
*.pem
*.ppk
id_*
```

- [ ] **Step 2: Create Dockerfile**

```dockerfile
FROM ubuntu:22.04

RUN apt-get update && apt-get install -y \
    openssh-server \
    openjdk-17-jre-headless \
    && rm -rf /var/lib/apt/lists/*

# SSH setup
RUN mkdir /var/run/sshd
RUN useradd -m moviebot && echo 'moviebot:moviebot' | chpasswd

# Configure SSH for key-only auth
RUN sed -i 's/#PasswordAuthentication yes/PasswordAuthentication no/' /etc/ssh/sshd_config
RUN sed -i 's/#PubkeyAuthentication yes/PubkeyAuthentication yes/' /etc/ssh/sshd_config

# App directories
RUN mkdir -p /opt/movie-crawler/data /opt/movie-crawler/backup /opt/movie-crawler/logs
RUN chown -R moviebot:moviebot /opt/movie-crawler

# Copy ssh key
COPY authorized_keys /home/moviebot/.ssh/authorized_keys
RUN chmod 700 /home/moviebot/.ssh && \
    chmod 600 /home/moviebot/.ssh/authorized_keys && \
    chown -R moviebot:moviebot /home/moviebot/.ssh

EXPOSE 22
CMD ["/usr/sbin/sshd", "-D"]
```

- [ ] **Step 3: Create run.sh**

```bash
#!/bin/bash
# Movie Crawler — run loop every 5 seconds
# Usage: ./run.sh

JAR_DIR="/opt/movie-crawler"
JAR_FILE="$JAR_DIR/movie-crawler-jar-with-dependencies.jar"

while true; do
    java -jar "$JAR_FILE"
    sleep 5
done
```

Make it executable: `chmod +x scripts/run.sh`

- [ ] **Step 4: Create deploy guide comment**

Add a deploy-guide comment block at the bottom of the Dockerfile (or as a separate `docs/deploy.md`) — user runs these steps manually:

```markdown
## Deploy Steps (run on HOST, not in container)

### 1. Generate SSH key pair
ssh-keygen -t ed25519 -f ~/.ssh/moviebot_key -N ""

### 2. Copy public key to docker/
cp ~/.ssh/moviebot_key.pub docker/authorized_keys

### 3. Build Docker image
docker build -t movie-crawler:latest -f docker/Dockerfile .

### 4. Run container
docker run -d --name movie-crawler -p 2222:22 movie-crawler:latest

### 5. Build fat JAR on host
mvn clean package

### 6. Copy JAR + script via SCP
scp -i ~/.ssh/moviebot_key -P 2222 \
  target/movie-crawler-service-1.0.0-jar-with-dependencies.jar \
  moviebot@localhost:/opt/movie-crawler/
scp -i ~/.ssh/moviebot_key -P 2222 \
  scripts/run.sh \
  moviebot@localhost:/opt/movie-crawler/

### 7. SSH in and start the crawler
ssh -i ~/.ssh/moviebot_key -p 2222 moviebot@localhost
chmod +x /opt/movie-crawler/run.sh
nohup /opt/movie-crawler/run.sh > /opt/movie-crawler/logs/runner.log 2>&1 &

### 8. Verify
tail -f /opt/movie-crawler/logs/crawler.log
ls -la /opt/movie-crawler/data/
ls -la /opt/movie-crawler/backup/
```

- [ ] **Step 5: Commit**

```bash
git add docker/Dockerfile scripts/run.sh .gitignore
git commit -m "feat(ex02): add Docker + deploy scripts"
```

---
