# Exercise 3: REST Web Service — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add REST API layer (`GET /movies?url=...`) returning JSON with standard envelope, built on existing Exercise 2 codebase without modifying any existing files (except pom.xml for JaCoCo).

**Architecture:** Controller → Service → Repository pattern. Constructor DI throughout. `WebServer.java` as new entry point (separate from `App.java`). `ApiResponse` + `ErrorDetail` records as response envelope. Spark exception handler maps typed exceptions to HTTP status codes. All JSON pretty-printed via Gson.

**Tech Stack:** Spark Java 2.9.4, Gson 2.11.0, JUnit 5.11.0, JaCoCo 0.8.12 (test coverage metrics)

## Global Constraints

- Java 17 source/target
- Encoding UTF-8
- Package root: `com.internship.moviecrawler`
- Do NOT modify existing Exercise 2 files (except pom.xml for JaCoCo)
- Constructor DI for all new classes
- All responses use Gson pretty-print (2-space indent)
- JSON envelope: `{success, status, data?, error?}`
- Test coverage metrics via JaCoCo

---

### Task 1: Add JaCoCo plugin for test coverage metrics

**Files:**
- Modify: `pom.xml`

**Interfaces:**
- Produces: `mvn test` generates JaCoCo report at `target/site/jacoco/index.html`
- Produces: `mvn verify` enforces coverage thresholds

- [ ] **Step 1: Add JaCoCo plugin to pom.xml**

In `pom.xml`, inside `<build><plugins>`, add after the existing maven-assembly-plugin:

```xml
<!-- JaCoCo — test coverage metrics -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
        <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>PACKAGE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.00</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

The coverage check starts at 0% minimum (ratchet up as tests grow). Report generates at `target/site/jacoco/index.html` after `mvn test`.

- [ ] **Step 2: Verify JaCoCo report generates**

Run: `mvn clean test`
Expected: BUILD SUCCESS, JaCoCo report at `target/site/jacoco/index.html`

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "chore(ex03): add JaCoCo plugin for test coverage metrics"
```

---

### Task 2: Create response DTOs — ApiResponse + ErrorDetail

**Files:**
- Create: `src/main/java/com/internship/moviecrawler/dto/ApiResponse.java`
- Create: `src/main/java/com/internship/moviecrawler/dto/ErrorDetail.java`

**Interfaces:**
- Produces: `new ApiResponse<>(true, 200, movie, null)` — success response
- Produces: `new ApiResponse<>(false, 404, null, new ErrorDetail("MOVIE_NOT_FOUND", "..."))` — error response
- `ApiResponse` is a generic Java record: `record ApiResponse<T>(boolean success, int status, T data, ErrorDetail error)`
- `ErrorDetail` is a Java record: `record ErrorDetail(String code, String message)`

- [ ] **Step 1: Create ErrorDetail record**

```java
package com.internship.moviecrawler.dto;

/**
 * Error payload in the API response envelope.
 * @param code    machine-readable error code (e.g. "MOVIE_NOT_FOUND")
 * @param message human-readable error description
 */
public record ErrorDetail(String code, String message) {}
```

- [ ] **Step 2: Create ApiResponse record**

```java
package com.internship.moviecrawler.dto;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Standard API response envelope.
 * <ul>
 *   <li>Success: {@code data} is populated, {@code error} is null</li>
 *   <li>Error:   {@code error} is populated, {@code data} is null</li>
 * </ul>
 *
 * @param <T>     type of the data payload
 * @param success whether the request succeeded
 * @param status  HTTP status code (mirrored in body for proxy resilience)
 * @param data    response payload (null on error)
 * @param error   error detail (null on success)
 */
public record ApiResponse<T>(boolean success, int status, T data, ErrorDetail error) {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    /**
     * Serialize this response to pretty-printed JSON.
     */
    public String toJson() {
        return GSON.toJson(this);
    }

    // ---- Static factories ----

    public static <T> ApiResponse<T> success(int status, T data) {
        return new ApiResponse<>(true, status, data, null);
    }

    public static <T> ApiResponse<T> error(int status, String code, String message) {
        return new ApiResponse<>(false, status, null, new ErrorDetail(code, message));
    }
}
```

- [ ] **Step 3: Verify Maven compiles**

Run: `mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/internship/moviecrawler/dto/
git commit -m "feat(ex03): add ApiResponse + ErrorDetail DTOs"
```

---

### Task 3: Create MovieService + custom exceptions

**Files:**
- Create: `src/main/java/com/internship/moviecrawler/service/MovieNotFoundException.java`
- Create: `src/main/java/com/internship/moviecrawler/service/MovieService.java`

**Interfaces:**
- Consumes: `MovieRepository` (existing interface from ex2), `Movie` (existing entity)
- Produces: `new MovieService(MovieRepository repo)`
- Produces: `MovieService.findByUrl(String encodedUrl)` → `Movie` (throws `MovieNotFoundException` if not found, `IllegalArgumentException` if url param null/blank)

- [ ] **Step 1: Create MovieNotFoundException**

```java
package com.internship.moviecrawler.service;

/**
 * Thrown when a movie URL is not found in the database.
 * Maps to HTTP 404 in the controller layer.
 */
public class MovieNotFoundException extends RuntimeException {

    private final String url;

    public MovieNotFoundException(String url) {
        super("No movie found for url: " + url);
        this.url = url;
    }

    public String getUrl() {
        return url;
    }
}
```

- [ ] **Step 2: Create MovieService**

```java
package com.internship.moviecrawler.service;

import com.internship.moviecrawler.model.Movie;
import com.internship.moviecrawler.repository.MovieRepository;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Business logic layer for movie lookup.
 * Validates input, decodes URL parameter, queries repository.
 */
public class MovieService {

    private final MovieRepository repo;

    public MovieService(MovieRepository repo) {
        this.repo = repo;
    }

    /**
     * Find a movie by its URL.
     *
     * @param encodedUrl the URL query parameter value (may be URL-encoded)
     * @return the matching Movie
     * @throws IllegalArgumentException if url is null or blank
     * @throws MovieNotFoundException  if no movie exists for this URL
     */
    public Movie findByUrl(String encodedUrl) {
        if (encodedUrl == null || encodedUrl.isBlank()) {
            throw new IllegalArgumentException("Query parameter 'url' is required");
        }

        String decodedUrl = URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8);

        return repo.findByUrl(decodedUrl)
                .orElseThrow(() -> new MovieNotFoundException(decodedUrl));
    }
}
```

- [ ] **Step 3: Verify Maven compiles**

Run: `mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/internship/moviecrawler/service/
git commit -m "feat(ex03): add MovieService with URL validation + MovieNotFoundException"
```

---

### Task 4: MovieService unit tests

**Files:**
- Create: `src/test/java/com/internship/moviecrawler/service/MovieServiceTest.java`

**Interfaces:**
- Consumes: `MovieService`, `SqliteMovieRepository` (in-memory), `Movie`
- Produces: 5 unit tests verifying service behavior

- [ ] **Step 1: Write all 5 MovieService tests**

```java
package com.internship.moviecrawler.service;

import com.internship.moviecrawler.model.Movie;
import com.internship.moviecrawler.repository.SqliteMovieRepository;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MovieServiceTest {

    private SqliteMovieRepository repo;
    private MovieService service;

    private static final String TEST_URL = "https://toivote.com/movie/test-uuid";

    @BeforeEach
    void setUp() {
        repo = new SqliteMovieRepository(":memory:");
        service = new MovieService(repo);
    }

    @AfterEach
    void tearDown() {
        repo.close();
    }

    // S1: findByUrl — movie exists → returns Movie
    @Test
    void findByUrl_Existing_ShouldReturnMovie() {
        Movie saved = new Movie(TEST_URL, "Test Phim", 2024, "Vietnam",
                List.of("Hành động"), List.of("Dir A"), List.of("Actor A"));
        repo.upsert(saved);

        Movie found = service.findByUrl(TEST_URL);

        assertNotNull(found);
        assertEquals(TEST_URL, found.getUrl());
        assertEquals("Test Phim", found.getTitle());
        assertEquals(2024, found.getReleaseYear());
        assertEquals("Vietnam", found.getCountry());
        assertEquals(List.of("Hành động"), found.getGenres());
        assertEquals(List.of("Dir A"), found.getDirectors());
        assertEquals(List.of("Actor A"), found.getActors());
    }

    // S2: findByUrl — null param → IllegalArgumentException
    @Test
    void findByUrl_NullParam_ShouldThrowIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.findByUrl(null));
        assertTrue(ex.getMessage().contains("required"));
    }

    // S3: findByUrl — blank param → IllegalArgumentException
    @Test
    void findByUrl_BlankParam_ShouldThrowIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.findByUrl("   "));
        assertTrue(ex.getMessage().contains("required"));
    }

    // S4: findByUrl — non-existent URL → MovieNotFoundException
    @Test
    void findByUrl_NonExistent_ShouldThrowMovieNotFoundException() {
        MovieNotFoundException ex = assertThrows(MovieNotFoundException.class,
                () -> service.findByUrl("https://toivote.com/movie/not-found"));
        assertEquals("https://toivote.com/movie/not-found", ex.getUrl());
    }

    // S5: findByUrl — URL-encoded input → decoded and found
    @Test
    void findByUrl_UrlEncoded_ShouldDecodeAndFind() {
        Movie saved = new Movie("https://toivote.com/movie/abc def", "Space Film", 2023, "",
                List.of(), List.of(), List.of());
        repo.upsert(saved);

        // "abc def" URL-encoded = "abc+def" or "abc%20def"
        Movie found = service.findByUrl("https://toivote.com/movie/abc%20def");

        assertNotNull(found);
        assertEquals("Space Film", found.getTitle());
    }
}
```

- [ ] **Step 2: Run tests**

Run: `mvn test -Dtest=MovieServiceTest`
Expected: All 5 tests PASS

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/internship/moviecrawler/service/
git commit -m "test(ex03): add MovieService unit tests — 5 cases"
```

---

### Task 5: Create MovieController

**Files:**
- Create: `src/main/java/com/internship/moviecrawler/controller/MovieController.java`

**Interfaces:**
- Consumes: `MovieService`, `Gson` (for serialization)
- Produces: `MovieController(MovieService service)`, `MovieController.registerRoutes()` — registers `GET /movies` and exception handlers on Spark

- [ ] **Step 1: Create MovieController**

```java
package com.internship.moviecrawler.controller;

import com.internship.moviecrawler.dto.ApiResponse;
import com.internship.moviecrawler.model.Movie;
import com.internship.moviecrawler.service.MovieNotFoundException;
import com.internship.moviecrawler.service.MovieService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

/**
 * Registers Spark routes for the Movie REST API.
 * All responses use the standard {@link ApiResponse} envelope.
 */
public class MovieController {

    private static final Logger log = LoggerFactory.getLogger(MovieController.class);

    private final MovieService service;

    public MovieController(MovieService service) {
        this.service = service;
    }

    /**
     * Register all routes and exception handlers with Spark.
     * Call once during server startup.
     */
    public void registerRoutes() {

        // GET /movies?url=<movie-url>
        Spark.get("/movies", (req, res) -> {
            String url = req.queryParams("url");
            Movie movie = service.findByUrl(url);

            res.status(200);
            res.type("application/json");
            return ApiResponse.success(200, movie).toJson();
        });

        // Global exception → JSON error envelope mapping
        Spark.exception(IllegalArgumentException.class, (ex, req, res) -> {
            res.status(400);
            res.type("application/json");
            res.body(ApiResponse.error(400, "MISSING_PARAM", ex.getMessage()).toJson());
        });

        Spark.exception(MovieNotFoundException.class, (ex, req, res) -> {
            res.status(404);
            res.type("application/json");
            res.body(ApiResponse.error(404, "MOVIE_NOT_FOUND", ex.getMessage()).toJson());
        });

        Spark.exception(Exception.class, (ex, req, res) -> {
            log.error("Unexpected error", ex);
            res.status(500);
            res.type("application/json");
            res.body(ApiResponse.error(500, "INTERNAL_ERROR", "An unexpected error occurred").toJson());
        });

        // Return 404 JSON (not HTML) for unmatched routes
        Spark.notFound((req, res) -> {
            res.type("application/json");
            return ApiResponse.error(404, "NOT_FOUND", "Endpoint not found: " + req.requestMethod() + " " + req.pathInfo()).toJson();
        });
    }
}
```

- [ ] **Step 2: Verify Maven compiles**

Run: `mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/internship/moviecrawler/controller/
git commit -m "feat(ex03): add MovieController with Spark routes + exception mapping"
```

---

### Task 6: Create WebServer entry point

**Files:**
- Create: `src/main/java/com/internship/moviecrawler/WebServer.java`

**Interfaces:**
- Consumes: `AppConfig`, `SqliteMovieRepository`, `MovieService`, `MovieController`
- Produces: `WebServer.main()` — starts Spark on port 8080 with all routes registered

- [ ] **Step 1: Create WebServer**

```java
package com.internship.moviecrawler;

import com.internship.moviecrawler.config.AppConfig;
import com.internship.moviecrawler.controller.MovieController;
import com.internship.moviecrawler.repository.MovieRepository;
import com.internship.moviecrawler.repository.SqliteMovieRepository;
import com.internship.moviecrawler.service.MovieService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

/**
 * Entry point for the MovieVault REST API server.
 * Initializes all layers (config → repository → service → controller) and starts Spark.
 *
 * Usage: {@code java -cp <fat-jar> com.internship.moviecrawler.WebServer}
 */
public class WebServer {

    private static final Logger log = LoggerFactory.getLogger(WebServer.class);

    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        AppConfig config = new AppConfig();

        // Init layers (constructor DI, bottom-up)
        MovieRepository repo = new SqliteMovieRepository(config.getDbPath());
        MovieService service = new MovieService(repo);
        MovieController controller = new MovieController(service);

        // Configure Spark
        Spark.port(DEFAULT_PORT);
        controller.registerRoutes();

        log.info("MovieVault API started on http://localhost:{}", DEFAULT_PORT);
        log.info("Endpoints:");
        log.info("  GET /movies?url=<movie-url>  — Lookup movie by URL");
    }
}
```

- [ ] **Step 2: Verify Maven compiles**

Run: `mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/internship/moviecrawler/WebServer.java
git commit -m "feat(ex03): add WebServer entry point for REST API"
```

---

### Task 7: MovieController integration tests

**Files:**
- Create: `src/test/java/com/internship/moviecrawler/controller/MovieControllerTest.java`

**Interfaces:**
- Consumes: `MovieController`, `Spark`, `SqliteMovieRepository` (in-memory), `MovieService`, `HttpClient`
- Produces: 4 integration tests hitting actual Spark endpoints

- [ ] **Step 1: Write all 4 controller integration tests**

```java
package com.internship.moviecrawler.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.internship.moviecrawler.dto.ApiResponse;
import com.internship.moviecrawler.model.Movie;
import com.internship.moviecrawler.repository.SqliteMovieRepository;
import com.internship.moviecrawler.service.MovieService;
import org.junit.jupiter.api.*;
import spark.Spark;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MovieControllerTest {

    private static final int TEST_PORT = 45678;
    private static final Gson GSON = new Gson();

    private SqliteMovieRepository repo;
    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        repo = new SqliteMovieRepository(":memory:");
        MovieService service = new MovieService(repo);
        MovieController controller = new MovieController(service);

        Spark.port(TEST_PORT);
        controller.registerRoutes();
        Spark.awaitInitialization();

        httpClient = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        Spark.stop();
        Spark.awaitStop();
        repo.close();
    }

    // C1: GET /movies?url=X — 200 + full JSON envelope
    @Test
    void getMovies_Existing_ShouldReturn200WithMovieData() throws Exception {
        repo.upsert(new Movie("https://toivote.com/movie/uuid-1", "Test Phim", 2024, "Vietnam",
                List.of("Hành động"), List.of("Dir X"), List.of("Actor Y")));

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + TEST_PORT + "/movies?url=https://toivote.com/movie/uuid-1"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("application/json", response.headers().firstValue("Content-Type").orElse(""));

        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
        assertTrue(json.get("success").getAsBoolean());
        assertEquals(200, json.get("status").getAsInt());
        assertNull(json.get("error"));

        JsonObject data = json.getAsJsonObject("data");
        assertEquals("Test Phim", data.get("title").getAsString());
        assertEquals(2024, data.get("releaseYear").getAsInt());
        assertEquals("Vietnam", data.get("country").getAsString());
        assertNotNull(data.get("createdAt"));
    }

    // C2: GET /movies (no param) → 400 + error envelope
    @Test
    void getMovies_MissingParam_ShouldReturn400() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + TEST_PORT + "/movies"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());

        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
        assertFalse(json.get("success").getAsBoolean());
        assertEquals(400, json.get("status").getAsInt());
        assertNull(json.get("data"));

        JsonObject error = json.getAsJsonObject("error");
        assertEquals("MISSING_PARAM", error.get("code").getAsString());
        assertTrue(error.get("message").getAsString().contains("required"));
    }

    // C3: GET /movies?url=NOTFOUND → 404 + error envelope
    @Test
    void getMovies_NotFound_ShouldReturn404() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + TEST_PORT + "/movies?url=https://toivote.com/movie/no-such-uuid"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());

        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
        assertFalse(json.get("success").getAsBoolean());
        assertEquals(404, json.get("status").getAsInt());

        JsonObject error = json.getAsJsonObject("error");
        assertEquals("MOVIE_NOT_FOUND", error.get("code").getAsString());
    }

    // C4: Pretty-print verification — response body contains indented JSON
    @Test
    void getMovies_Existing_ShouldReturnPrettyPrintedJson() throws Exception {
        repo.upsert(new Movie("https://toivote.com/movie/uuid-pretty", "Pretty", null, "",
                List.of(), List.of(), List.of()));

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + TEST_PORT + "/movies?url=https://toivote.com/movie/uuid-pretty"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        String body = response.body();
        assertTrue(body.contains("\n"), "Response should be pretty-printed (contain newlines)");
        assertTrue(body.contains("  "), "Response should contain 2-space indent");
    }
}
```

- [ ] **Step 2: Run integration tests**

Run: `mvn test -Dtest=MovieControllerTest`
Expected: All 4 tests PASS

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/internship/moviecrawler/controller/
git commit -m "test(ex03): add MovieController integration tests — 4 cases"
```

---

### Task 8: Run full test suite + JaCoCo report

**Files:**
- None (verification only)

- [ ] **Step 1: Run all tests with coverage**

Run: `mvn clean test`
Expected: ALL 31 tests PASS (22 existing ex02 + 5 MovieService + 4 MovieController)

- [ ] **Step 2: Verify JaCoCo report**

Open `target/site/jacoco/index.html` in browser or check CSV:
Run: `cat target/site/jacoco/index.html | grep -o "Total.*[0-9]*%"` (or use desktop-commander to open)

Expected: Coverage report generated showing line/branch coverage for all packages

- [ ] **Step 3: Update CLAUDE.md with new modules and commands**

Add to `CLAUDE.md` under "Build & Test Commands":
```markdown
# Run tests with coverage report
mvn clean test
# Report at: target/site/jacoco/index.html
```

Add under "Architecture" the new packages:
```markdown
├── controller/
│   └── MovieController.java    # Spark routes + response formatting
├── service/
│   ├── MovieService.java       # Business logic
│   └── MovieNotFoundException.java  # 404 error
└── dto/
    ├── ApiResponse.java        # Standard JSON envelope
    └── ErrorDetail.java        # Error code + message
```

Add entry point:
```markdown
| **`WebServer.main()`** | ✅ Có | Start REST API server on port 8080 |
```

- [ ] **Step 4: Run full test suite one final time**

Run: `mvn clean test`
Expected: BUILD SUCCESS, 31 tests, 0 failures

- [ ] **Step 5: Commit CLAUDE.md update**

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md for exercise 3"
```
