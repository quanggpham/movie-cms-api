# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Compile
mvn compile

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=MovieParserTest

# Run a single test method
mvn test -Dtest=MovieParserTest#parse_EnchantedFixture_ShouldExtractAllFields

# Build fat JAR (with all dependencies)
mvn clean package -P standalone

# Run all tests with JaCoCo coverage report
mvn clean test
# Coverage report: target/site/jacoco/index.html

# Run the crawler (batch mode — exits after crawl completes)
java -jar target/movie-crawler-service-1.0.0-jar-with-dependencies.jar

# Run the REST API server (Exercise 3)
java -cp target/movie-crawler-service-1.0.0-jar-with-dependencies.jar com.internship.moviecrawler.WebServer
```

## Project Roadmap & Current State

This is a 5-exercise internship project. **Currently only Exercise 2 is implemented** (Exercises 3-6 are pending):

| Exercise | Status | What it adds |
|----------|--------|-------------|
| **2** — Web Crawler & SQLite | ✅ Done | `App.java`, `MovieFetcher`, `MovieParser`, `SqliteMovieRepository`, `DatabaseBackup` |
| **3** — REST Web Service | ✅ Done | `WebServer.java`, `MovieController`, `MovieService`, `ApiResponse` envelope |
| **4** — Custom CacheTTL | ❌ Not started | `CacheTTL<K,V> implements Map<K,V>` with TTL per entry |
| **5** — Guava Cache | ❌ Not started | Replace custom cache with Guava, Git conflict resolution |
| **6** — Auth & Rate Limiting | ❌ Not started | `POST /login`, rate limiter, Docker deploy, JVM heap tuning |

Dependencies for upcoming exercises are already in `pom.xml`: `spark-core` (ex3), `guava` (ex5).

## Architecture

**Layered monolith** — code organized into packages by layer, each with a single responsibility:

```
src/main/java/com/internship/moviecrawler/
├── App.java                     # Main orchestrator — wires everything
├── config/
│   └── AppConfig.java           # Reads config.properties, typed getters with defaults
├── crawler/
│   ├── FetchException.java      # Base exception + TransientFetchException / PermanentFetchException subclasses
│   ├── MovieFetcher.java        # HTTP client (Java HttpClient) with retry + backoff
│   ├── MovieParser.java         # HTML → Movie (JSON-LD primary, CSS fallback)
│   ├── ParseException.java      # Thrown when title can't be extracted
│   └── UrlCollector.java        # Standalone tool — discovers movie URLs from toivote.com
├── model/
│   └── Movie.java               # POJO: 11 fields, no behavior
├── repository/
│   ├── MovieRepository.java     # Interface (for future swap: mock, cache, etc.)
│   └── SqliteMovieRepository.java # SQLite: upsert via ON CONFLICT, list fields as JSON
└── backup/
    └── DatabaseBackup.java      # Copies .db → backup/movies_YYYYMMDD_HHmmss.db
├── controller/                # (ex3) REST API layer
│   └── MovieController.java   # Spark routes + response formatting
├── service/                   # (ex3) Business logic
│   ├── MovieService.java      # URL validation + lookup
│   └── MovieNotFoundException.java  # 404 error
└── dto/                       # (ex3) API data transfer objects
    ├── ApiResponse.java       # Standard JSON envelope {success, status, data, error}
    └── ErrorDetail.java       # Error code + message
```

### Key Design Rules

- **`App.java` is the only class that knows about all others.** Lower layers never import each other: `MovieFetcher` doesn't know `MovieParser`, `MovieParser` doesn't know the repository, repository doesn't know `DatabaseBackup`.
- **Repository uses interface-first** so tests can use `:memory:` SQLite and future exercises can wrap it with caching.
- **Title is required** — `MovieParser` throws `ParseException` if missing. All other fields default gracefully (null, "", or empty list).

### Data Flow (Crawl Pipeline)

```
urls.txt → freshness check (skip if <24h old) → MovieFetcher.fetch(url) → HTML
→ MovieParser.parse(html, url) → Movie object → SqliteMovieRepository.upsert(movie)
→ Thread.sleep(1.5s) → repeat → close DB → DatabaseBackup.backup()
```

## Testing

- **JUnit 5 + WireMock** for HTTP mocking, **in-memory SQLite** (`:memory:`) for repository tests
- Test HTML fixtures in `src/test/resources/html/` (enchanted.html, minimal.html, malformed.html)
- WireMock tests use `@RegisterExtension` with dynamic port — no port conflicts
- Repository tests use `@BeforeEach`/`@AfterEach` to create/close in-memory DB per test

**Test coverage** via JaCoCo (`mvn clean test` → `target/site/jacoco/index.html`). Coverage thresholds in `pom.xml` start at 0% minimum — ratchet up as tests grow.

## Configuration

All in `src/main/resources/config.properties` — fetch timeouts, retry count, delay, DB path, etc. Fallback values hardcoded in `AppConfig.java`. Logging via `logback.xml` (rolling file, daily, 30-day retention; separate file for failed URLs).

## API (Exercise 3)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|:---:|
| `GET` | `/movies?url=<movie-url>` | Lookup movie by URL | ❌ |

All responses use a JSON envelope: `{"success": true/false, "status": <http-code>, "data": ..., "error": {"code": "...", "message": "..."}}`. Gson pretty-prints all output. `null` fields are omitted.

## Database

Single table `movies` with upsert via `ON CONFLICT(url) DO UPDATE`. List fields (`genres`, `directors`, `actors`) stored as JSON text — serialized/deserialized via Gson.

## Docker Deployment

`docker/Dockerfile` — Ubuntu 22.04 + OpenSSH + Java 17. Key-only SSH auth. `scripts/run.sh` loops the JAR every 5 seconds. JVM heap: initial 125MB, max 512MB (configured externally).
