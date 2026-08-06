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

# Build fat JAR (with all dependencies, manifest points to App.java)
mvn clean package -P standalone

# Run all tests with JaCoCo coverage report
mvn clean test
# Coverage report: target/site/jacoco/index.html

# Run the crawler (batch mode — exits after crawl completes)
java -jar target/movie-crawler-service-1.0.0-jar-with-dependencies.jar

# Run the REST API server
java -cp target/movie-crawler-service-1.0.0-jar-with-dependencies.jar com.internship.moviecrawler.WebServer
```

## Project Roadmap & Current State

All 5 exercises are implemented:

| Exercise | Status | What it adds |
|----------|--------|-------------|
| **2** — Web Crawler & SQLite | ✅ Done | `App.java`, `MovieFetcher`, `MovieParser`, `SqliteMovieRepository`, `DatabaseBackup` |
| **3** — REST Web Service | ✅ Done | `WebServer.java`, `MovieController`, `MovieService`, `ApiResponse` envelope |
| **4** — Custom CacheTTL | ✅ Done (replaced) | Originally `CacheTTL<K,V>` — since replaced by Guava in exercise 5 |
| **5** — Guava Cache | ✅ Done | `CachedMovieRepository` wrapping `SqliteMovieRepository` with Guava `Cache` (access + write TTL, hit rate stats) |
| **6** — Auth & Rate Limiting | ✅ Done | `POST /login`, `AuthFilter` with sliding-window rate limiter (2 req/5s + 10 req/60s), Docker deploy, JVM heap tuning |

## Architecture

**Layered monolith** — code organized into packages by layer, each with a single responsibility:

```
src/main/java/com/internship/moviecrawler/
├── App.java                     # Main orchestrator for crawl pipeline
├── WebServer.java               # REST API entry point — wires all layers and starts Spark
├── config/
│   └── AppConfig.java           # Reads config.properties, typed getters with defaults
├── crawler/
│   ├── FetchException.java      # Base + TransientFetchException / PermanentFetchException
│   ├── MovieFetcher.java        # HTTP client (Java HttpClient) with retry + exponential backoff
│   ├── MovieParser.java         # HTML → Movie (JSON-LD primary, CSS fallback)
│   ├── ParseException.java      # Thrown when title can't be extracted
│   └── UrlCollector.java        # Standalone tool — discovers movie URLs from toivote.com
├── model/
│   └── Movie.java               # POJO: 11 fields, no behavior
├── repository/
│   ├── MovieRepository.java     # Interface (for DI, testing with :memory: SQLite)
│   ├── SqliteMovieRepository.java # SQLite: upsert via ON CONFLICT, list fields as JSON
│   └── CachedMovieRepository.java # Guava Cache decorator — delegates to SQLite, records stats
├── backup/
│   └── DatabaseBackup.java      # Copies .db → backup/movies_YYYYMMDD_HHmmss.db
├── controller/
│   ├── MovieController.java     # Spark routes (GET /movies, GET /cache/stats) + exception handlers
│   └── AuthFilter.java          # Bearer-token auth + sliding-window rate limiter (Spark before() filter)
├── service/
│   ├── MovieService.java        # Business logic — URL validation + lookup
│   └── MovieNotFoundException.java  # 404 error
└── dto/
    ├── ApiResponse.java         # Standard JSON envelope {success, status, data, error} (Java record)
    └── ErrorDetail.java         # Error code + message
```

### Key Design Rules

- **`App.java` and `WebServer.java` are the only classes that know about all others.** Lower layers never import each other: `MovieFetcher` doesn't know `MovieParser`, `MovieParser` doesn't know the repository, repository doesn't know `DatabaseBackup`.
- **Repository uses interface-first** so tests can use `:memory:` SQLite and the cache layer (`CachedMovieRepository`) can wrap transparently.
- **Title is required** — `MovieParser` throws `ParseException` if missing. All other fields default gracefully (null, "", or empty list).

### Data Flow (Crawl Pipeline)

```
urls.txt → freshness check (skip if <24h old) → MovieFetcher.fetch(url) → HTML
→ MovieParser.parse(html, url) → Movie object → SqliteMovieRepository.upsert(movie)
→ Thread.sleep(1.5s) → repeat → close DB → DatabaseBackup.backup()
```

### Data Flow (REST API)

```
HTTP request → AuthFilter (token + rate-limit check) → MovieController → MovieService
→ CachedMovieRepository (cache hit? → return | cache miss? → SqliteMovieRepository) → ApiResponse JSON
```

## API (Exercises 3-6)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|:---:|
| `POST` | `/login` | Exchange credentials for bearer token | ❌ |
| `GET` | `/movies?url=<movie-url>` | Lookup movie by URL | ✅ Bearer |
| `GET` | `/cache/stats` | Cache hit rate & current size | ✅ Bearer |

All responses use a JSON envelope: `{"success": true/false, "status": <http-code>, "data": ..., "error": {"code": "...", "message": "..."}}`. Gson pretty-prints all output. `null` fields are omitted.

### Auth & Rate Limiting

- **`POST /login`** — accepts `{"username": "...", "password": "..."}`, returns `{"token": "<uuid>", "expiresIn": 3600}`. Credentials in `config.properties` (default: `admin`/`secret`).
- **All other routes** require `Authorization: Bearer <token>` header. Tokens expire after 1 hour; a background thread evicts expired tokens every 10 minutes.
- **Sliding-window rate limiter** per token: max 2 requests per 5 seconds AND max 10 requests per 60 seconds. Exceeding either window returns 429. Stale windows are evicted every 5 minutes.

## Guava Cache

`CachedMovieRepository` wraps any `MovieRepository` with a Guava `Cache` configured with:
- `expireAfterAccess` (idle TTL) — default 10s
- `expireAfterWrite` (absolute TTL) — default 20s
- `recordStats()` — hit rate available via `GET /cache/stats`

The cache is a transparent decorator: all `MovieRepository` methods delegate through. Only `findByUrl` checks/updates the cache.

## Configuration

All in `src/main/resources/config.properties` — fetch timeouts, retry count, delay, DB path, auth credentials, etc. Fallback values hardcoded in `AppConfig.java`. Logging via `logback.xml` (rolling file, daily, 30-day retention; separate file for failed URLs).

## Database

Single table `movies` with upsert via `ON CONFLICT(url) DO UPDATE`. List fields (`genres`, `directors`, `actors`) stored as JSON text — serialized/deserialized via Gson.

## Docker Deployment

`docker/Dockerfile` — Ubuntu 22.04 + OpenSSH + Java 17 (both JRE and JDK) + Maven + Git. Key-only SSH auth (no password). Exposes ports 22 (SSH) and 8080 (API).

`scripts/run.sh` — production runner: starts WebServer in background, then loops the crawler JAR every 5 seconds. JVM heap: `-Xms125m -Xmx512m`.

## Testing

- **JUnit 5 + WireMock** for HTTP mocking, **in-memory SQLite** (`:memory:`) for repository tests
- Test HTML fixtures in `src/test/resources/html/` (enchanted.html, minimal.html, malformed.html)
- WireMock tests use `@RegisterExtension` with dynamic port — no port conflicts
- Repository tests use `@BeforeEach`/`@AfterEach` to create/close in-memory DB per test
- `MovieControllerTest` starts Spark on a custom port (45678) and sends real HTTP requests via `java.net.http.HttpClient`
- `CachedMovieRepositoryTest` uses a `StubRepo` inner class that counts delegate calls — verifies cache hit/miss behavior

**Test coverage** via JaCoCo (`mvn clean test` → `target/site/jacoco/index.html`). Coverage thresholds in `pom.xml` start at 0% minimum — ratchet up as tests grow.
