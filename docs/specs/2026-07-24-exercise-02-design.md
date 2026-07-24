# Exercise 2: Movie Crawler — Design Spec

**Date:** 2026-07-24
**Status:** Approved
**Project:** `exercise-02-06-movie-crawler-service`

---

## 1. Overview

Build a web crawler that fetches ~100 movie detail pages from toivote.com, extracts structured data (title, year, country, genres, directors, actors), stores it in SQLite with backup, and deploys on a Docker Linux VM running every 5 seconds via shell script.

This is Phase 2 of a 5-phase project (exercises 2→6). Architecture is designed to allow adding web service (ex3), custom cache (ex4), Guava cache (ex5), and auth/rate-limiting (ex6) with minimal refactoring — each exercise gets its own commit.

---

## 2. Package Structure

```
com.internship.moviecrawler/
├── App.java                    // Main entry point — orchestrator
├── model/
│   └── Movie.java              // Entity: id, url, title, releaseYear, country, genres, directors, actors, createdAt, updatedAt, lastCrawledAt
├── crawler/
│   ├── MovieFetcher.java       // HTTP fetch + retry. Input: URL → Output: raw HTML String
│   └── MovieParser.java        // Stateless CSS-selector parser. Input: HTML + URL → Output: Movie
├── repository/
│   ├── MovieRepository.java    // Interface: upsert(Movie), findAll(), findById(), existsByUrl(url)
│   └── SqliteMovieRepository.java // SQLite implementation with JSON column deserialization
├── config/
│   └── AppConfig.java          // Loads config.properties
└── backup/
    └── DatabaseBackup.java     // Copies .db file after connection close
```

**Responsibility boundaries:**
- `MovieFetcher` — only HTTP. Doesn't know about Movie, doesn't know about parsing.
- `MovieParser` — only parsing. Stateless, no I/O beyond the HTML string it receives.
- `App.main()` — orchestrator: iterates URLs, calls fetcher → parser → repository → backup.

---

## 3. Data Model

### 3.1 Movie Entity

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `id` | `Long` | auto | — | Primary key, auto-increment |
| `url` | `String` | yes | — | UNIQUE, natural key for dedup |
| `title` | `String` | yes | — | Fail entire parse if missing |
| `releaseYear` | `Integer` | no | `null` | Extract first number from year string |
| `country` | `String` | no | `""` | |
| `genres` | `List<String>` | no | `[]` | Stored as JSON array text in DB |
| `directors` | `List<String>` | no | `[]` | Stored as JSON array text in DB |
| `actors` | `List<String>` | no | `[]` | Stored as JSON array text in DB |
| `createdAt` | `String` | auto | `datetime('now')` | Set on INSERT only |
| `updatedAt` | `String` | auto | `datetime('now')` | Set on INSERT, updated explicitly on UPDATE |
| `lastCrawledAt` | `String` | auto | `datetime('now')` | Used for 24h freshness check |

### 3.2 SQLite Schema

```sql
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
);
```

### 3.3 Upsert Logic

```sql
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
    last_crawled_at = datetime('now');
```

`ON CONFLICT ... DO UPDATE` preserves `id` and `created_at` across updates, unlike `INSERT OR REPLACE` which deletes and re-inserts the row.

### 3.4 JSON Column Handling

- **On write:** `genres`, `directors`, `actors` serialized via Gson `toJson(List)`.
- **On read:** Repository deserializes JSON text → `List<String>` via Gson `fromJson(String, TypeToken)`.
- **Null safety:** If column is `null` or empty, return `Collections.emptyList()`, never null.

---

## 4. MovieFetcher — HTTP Layer

### 4.1 Contract

```java
public class MovieFetcher {
    public String fetch(String url) throws FetchException;
}
```

### 4.2 Config

```properties
fetch.connect.timeout.ms=5000
fetch.request.timeout.ms=10000
fetch.max.retries=3
fetch.user.agent=Mozilla/5.0 (compatible; MovieCrawler/1.0)
```

- Uses Java `HttpClient` (Java 17+).
- Redirect policy: `NORMAL` (auto-follow 3xx).
- Content-Type validation: response must contain `text/html` (substring match, not exact — header may include charset).

### 4.3 Retry Strategy

**1 initial request + max 3 retries = 4 total attempts.**
Backoff: 1s → 2s → 4s.

### 4.4 Error Classification

| Condition | Exception Type | Retry? |
|---|---|---|
| 200 with `text/html` Content-Type | Success (return HTML) | — |
| 404, 400, 403 | `PermanentFetchException` | No — skip immediately |
| 408 Request Timeout, 429 Too Many Requests | `TransientFetchException` | Yes |
| 5xx, IOException, TimeoutException | `TransientFetchException` | Yes |
| `InterruptedException` | Restore interrupt flag, wrap in `FetchException` | No |

For 429: if `Retry-After` header present, use it as backoff duration; otherwise fall back to exponential backoff.

### 4.5 What MovieFetcher Does NOT Do

- Does not parse HTML.
- Does not know about Movie entity.
- Does not manage inter-URL delay (that's the orchestrator's job).
- Does not decide skip/refresh policy.

---

## 5. MovieParser — Parsing Layer

### 5.1 Contract

```java
public class MovieParser {
    public Movie parse(String html, String url) throws ParseException;
}
```

### 5.2 Parsing Strategy

CSS selectors determined by analyzing sample toivote.com pages at implementation time. Six fields extracted:

1. **Title** — from `<title>` or `<h1>`. If missing → throw `ParseException` (whole URL fails).
2. **Release Year** — extract first 4-digit number from year element. If missing → `null`.
3. **Country** — from country/location element. If missing → `""`.
4. **Genres** — split by comma/separator, trim each item. If missing → `[]`.
5. **Directors** — extract list, trim each. If missing → `[]`.
6. **Actors** — extract list, trim each. If missing → `[]`.

### 5.3 Failure Policy

| Failure | Behavior |
|---|---|
| Title missing | Throw `ParseException` → orchestrator logs WARN, skips URL |
| Any other field missing | Field gets default value (`null`, `""`, or `[]`), Movie still saved |
| HTML null/empty | Throw `ParseException` |

Parser is stateless and pure — no side effects, no I/O.

---

## 6. Flow & Orchestration

### 6.1 Main Flow (`App.main()`)

```
1. Load config from classpath:config.properties
2. Initialize SQLite repository → CREATE TABLE IF NOT EXISTS
3. Read urls.txt from classpath → filter empty/comment lines → deduplicate
4. Counters: total, crawled (inserted/updated), skipped, fetchFailed, parseFailed
5. For each URL:
   a. Query last_crawled_at from DB
      → If exists AND last_crawled_at < 24h ago → skip + increment "skipped"
   b. MovieFetcher.fetch(url) — with retry
      → FetchException? → log ERROR + "fetchFailed" + skip to next URL
   c. MovieParser.parse(html, url)
      → ParseException? → log WARN + "parseFailed" + skip to next URL
   d. repository.upsert(movie)
      → Check affected rows to determine inserted vs updated
   e. Delay ~1.5s (Thread.sleep(1500)) in finally block — applies after both success and failure, to respect target server
6. Close repository connection
7. DatabaseBackup.backup() → backup/movies_YYYYMMDD_HHmmss.db
8. Log summary with all counters + elapsed time
```

### 6.2 Freshness Policy

- Column `last_crawled_at` tracks when each movie was last fetched.
- URL is **skipped** if `last_crawled_at` is within 24 hours.
- After 24h, URL is re-fetched and upserted (data may have changed on site).
- This is the key design decision that makes the 5-second cron loop viable: batch 1 crawls all, batches 2-N are mostly no-op until the next day.

### 6.3 Summary Output

```
========== Crawl Summary ==========
Total URLs:         100
Crawled:             95
  Inserted:          80
  Updated:           15
Skipped (fresh):      3
Fetch failed:         2
Parse failed:         0
Elapsed:           2m 34s
====================================
```

---

## 7. Backup

- `DatabaseBackup.backup(Path dbPath, Path backupDir)` copies the SQLite `.db` file.
- Called **after** repository connection is **closed** — avoids WAL/corruption issues.
- Output filename: `backup/movies_20260724_153045.db` (timestamp of backup moment).
- Backup directory is kept flat (no rotation logic; old backups accumulate, cleaned manually).

---

## 8. File Layout

```
exercise-02-06-movie-crawler-service/
├── pom.xml
├── src/main/java/com/internship/moviecrawler/
│   ├── App.java
│   ├── model/Movie.java
│   ├── crawler/
│   │   ├── MovieFetcher.java
│   │   └── MovieParser.java
│   ├── repository/
│   │   ├── MovieRepository.java
│   │   └── SqliteMovieRepository.java
│   ├── config/AppConfig.java
│   └── backup/DatabaseBackup.java
├── src/main/resources/
│   ├── config.properties
│   ├── logback.xml
│   └── urls.txt
├── src/test/java/com/internship/moviecrawler/
│   ├── crawler/MovieParserTest.java
│   └── repository/SqliteMovieRepositoryTest.java
├── src/test/resources/html/
│   ├── titanic.html          // Full fields sample
│   ├── minimal.html           // Title + year only
│   └── malformed.html         // No title element
├── data/                      // Runtime: SQLite DB (gitignored)
├── backup/                    // Runtime: .db backups (gitignored)
├── logs/                      // Runtime: log files (gitignored)
├── docker/
│   ├── Dockerfile
│   └── authorized_keys
├── scripts/
│   └── run.sh
└── docs/
```

---

## 9. Testing

### 9.1 Verification Matrix

#### MovieParser (Unit, JUnit 5)

| # | Test | Input | Expected | Category |
|---|---|---|---|---|
| P1 | All 6 fields present | Full HTML sample | Movie with all fields populated, genres.size()=3 | Happy path |
| P2 | Only title + year | Minimal HTML | Movie with title/year, all lists = [] | Resilience |
| P3 | No title element | HTML without \<title\> or \<h1\> | ParseException | Error |
| P4 | Null/empty HTML | null, "" | ParseException | Boundary |
| P5 | Year with mixed text | "Năm 2023 (tái bản)" | releaseYear = 2023 | Normalize |
| P6 | Comma-separated genres | "Hành động, Tình cảm, Hài" | genres = [trimmed items] | Normalize |
| P7 | Single director | One name | directors = [single-item list] | Data type |
| P8 | Non-UTF-8 charset | ISO-8859-1 HTML | Correct Unicode output | Encoding |

#### SqliteMovieRepository (Integration, SQLite in-memory)

| # | Test | Input | Expected | Category |
|---|---|---|---|---|
| R1 | upsert() — insert new | New Movie | Row persisted, id assigned, created_at set | Happy path |
| R2 | upsert() — update existing | Same URL, different title | id preserved, created_at preserved, updated_at changed, title updated | Idempotency |
| R3 | upsert() — isolation | Insert A, insert B, upsert A | B unaffected by A's update | Isolation |
| R4 | existsByUrl() — found | Existing URL | true | Happy path |
| R5 | existsByUrl() — not found | Non-existing URL | false | Edge |
| R6 | findAll() | 3 movies in DB | List size = 3 | Happy path |
| R7 | JSON deserialization | genres column = "[\"A\",\"B\"]" | getGenres() returns List.of("A", "B") | Serialization |
| R8 | Null JSON columns | genres = null | getGenres() returns [] (not null) | Resilience |

#### MovieFetcher (Optional, WireMock)

| # | Test | Input | Expected | Category |
|---|---|---|---|---|
| F1 | 200 text/html | WireMock 200 | HTML string returned | Happy path |
| F2 | 404 | WireMock 404 | PermanentFetchException, no retry | Error |
| F3 | 500×3 then 200 | WireMock sequence | Succeeds on 4th attempt | Retry |
| F4 | 500×4 | WireMock 4×500 | TransientFetchException after all retries | Retry exhaust |
| F5 | Timeout >10s | WireMock delay | TransientFetchException, retries | Timeout |

#### End-to-End (Manual)

| # | Criterion | Method |
|---|---|---|
| E1 | `mvn clean package` succeeds | Check fat JAR in target/ |
| E2 | First run crawls ~100 URLs | Check movies.db has rows |
| E3 | Second run within 24h | All URLs skipped as fresh |
| E4 | After 24h, modified data | Updated count > 0 |
| E5 | One 404 in URL list | That URL logged, others still crawl |
| E6 | Backup file exists | backup/movies_*.db present, size ≈ active DB |
| E7 | SSH key auth works | `ssh -i key user@container` no password prompt |
| E8 | Shell loop runs every 5s | New log line every 5s |

### 9.2 Test Summary

| Layer | Type | Cases | Tool |
|---|---|---|---|
| Parser | Unit | 8 | JUnit 5 |
| Repository | Integration | 8 | JUnit 5 + SQLite in-memory |
| Fetcher | Integration (optional) | 5 | WireMock |
| E2E | Manual | 8 | — |

---

## 10. Logging

- **Main log** (`logs/crawler.log`): INFO-level progress (URL count, crawl results), WARN for parse failures, ERROR for fetch failures.
- **Failed URLs** (`logs/failed-urls.txt`): Dedicated file for failed URL list, written at end of batch.
- Config via `logback.xml`. Simple rolling: 10MB max per file, 30 days retention.

---

## 11. Docker & Deploy

### 11.1 Dockerfile

Base image: `ubuntu:22.04` with `openssh-server`, `openjdk-17-jre-headless`.

- Non-root user `moviebot`.
- SSH key auth only (password auth disabled).
- Port 22 exposed.
- Note: cron is not used (minimum granularity 1 minute vs 5-second requirement). `run.sh` uses a `while true` loop instead.

### 11.2 Shell Script (`run.sh`)

```bash
#!/bin/bash
while true; do
    java -jar /opt/movie-crawler/movie-crawler-jar-with-dependencies.jar
    sleep 5
done
```

Uses `while true` loop rather than cron because cron's minimum granularity is 1 minute.

### 11.3 Deploy Steps

```
1. Generate SSH key pair (ed25519) on host
2. Copy public key to docker/authorized_keys
3. docker build -t movie-crawler .
4. docker run -d --name movie-crawler -p 2222:22 movie-crawler
5. scp JAR + run.sh into container via port 2222
6. SSH in, chmod +x run.sh, launch with nohup
7. Verify via logs and DB content
```

The user will be guided through each step; no commands run without explicit approval.

---

## 12. Build

- Maven `maven-assembly-plugin` with `jar-with-dependencies` descriptor.
- Single fat JAR containing all dependencies.
- Main class: `com.internship.moviecrawler.App`.
- Command: `mvn clean package`

---

## 13. Forward Compatibility

This design intentionally enables exercises 3→6 with minimal refactoring:

| Exercise | What's added | Where |
|---|---|---|
| 3 — Web Service | REST endpoints returning JSON | New `web/` package, `SparkWebService.java` |
| 4 — Custom Cache | `CacheTTL<K,V> implements Map<K,V>` | New `cache/` package, wraps `MovieRepository` |
| 5 — Guava Cache | Replace CacheTTL with Guava | Modify cache package, add Guava dep |
| 6 — Auth + Rate Limit | Login filter, rate limiter | New `auth/` and `ratelimit/` packages |

Each exercise gets its own git commit on the same branch.

---

## 14. Resources

- **URL source:** `src/main/resources/urls.txt` — ~100 movie URLs, one per line. Lines starting with `#` are comments.
- **Selector discovery:** To be determined at implementation by analyzing real toivote.com HTML samples.
- **Reference:** `docs/assignment.pdf` pages 1-2.
