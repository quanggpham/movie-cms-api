# Exercise 3: REST Web Service — Design Spec

**Date:** 2026-07-29
**Status:** Draft
**Project:** Exercise 3/6 — MovieVault CMS

---

## 1. Overview

Add a REST web service layer on top of the Exercise 2 SQLite database. Single endpoint `GET /movies?url=<movie-url>` returns pretty-printed JSON. All responses include a standard envelope with HTTP status code.

## 2. API Contract

### `GET /movies?url=<movie-url>`

**Success Response (200):**
```json
{
  "success": true,
  "status": 200,
  "data": {
    "id": 1,
    "url": "https://toivote.com/movie/2d9acb2c-...",
    "title": "Phép Thuật",
    "releaseYear": 2007,
    "country": "",
    "genres": ["Nhạc kịch", "Viễn tưởng", "Hài hước"],
    "directors": ["Kevin Lima"],
    "actors": ["Amy Adams", "Patrick Dempsey", "..."],
    "createdAt": "2026-07-24 12:23:15",
    "updatedAt": "2026-07-24 12:23:15",
    "lastCrawledAt": "2026-07-24 12:23:15"
  }
}
```

**Error Responses:**

| Status | Code | Condition |
|--------|------|-----------|
| 400 | `MISSING_PARAM` | Query param `url` is missing or blank |
| 404 | `MOVIE_NOT_FOUND` | URL not in database |
| 500 | `INTERNAL_ERROR` | Unexpected runtime / DB error |

**Error Body:**
```json
{"success": false, "status": 400, "error": {"code": "MISSING_PARAM", "message": "..."}}
```

**All responses use Gson pretty-print (2-space indent).**

## 3. Architecture

```
WebServer.main()
├── MovieController.java       # Spark routes + request/response formatting
│   └── MovieService.java      # Business logic: find by URL, validate input
│       └── MovieRepository     # Interface from Ex2 (SqliteMovieRepository)
```

### New Files

| File | Package | Responsibility |
|------|---------|---------------|
| `WebServer.java` | root | Entry point — start Spark, init repository + service + controller |
| `MovieController.java` | `controller` | Define Spark routes, parse request, format JSON response envelope |
| `MovieService.java` | `service` | Business logic: validate input, query repository |
| `ApiResponse.java` | `controller` (or `dto`) | Response DTOs: `success` / `data` / `error` envelope |

### Unchanged Files

Existing Exercise 2 code is **untouched** — `App.java` continues as crawl entry, `MovieRepository` interface reused as-is.

## 4. Component Details

### 4.1 WebServer (entry point)

```java
public static void main(String[] args) {
    AppConfig config = new AppConfig();
    MovieRepository repo = new SqliteMovieRepository(config.getDbPath());
    MovieService service = new MovieService(repo);
    MovieController controller = new MovieController(service);
    // Spark starts on port 8080
}
```

### 4.2 MovieController

- Registers `GET /movies` route
- Parses `request.queryParams("url")`
- On success: wraps Movie in ApiResponse envelope, returns as pretty-printed JSON
- On error: catches typed exceptions → appropriate HTTP status + error envelope
- Uses Gson for serialization

### 4.3 MovieService

- `findByUrl(String encodedUrl)`: URL-decodes the input, calls `repo.findByUrl(url)`, throws `MovieNotFoundException` if empty
- Validates that url param is present and non-blank → throws `BadRequestException`

### 4.4 ApiResponse

Generic response wrapper used across all endpoints:
```java
record ApiResponse<T>(boolean success, int status, T data, ErrorDetail error) {}
record ErrorDetail(String code, String message) {}
```

For success: `data` = Movie, `error` = null
For error: `data` = null, `error` = ErrorDetail

## 5. Error Handling Strategy

```
Spark Exception handler
  ├── BadRequestException      → 400 JSON
  ├── MovieNotFoundException   → 404 JSON
  └── Exception (catch-all)    → 500 JSON  (log stacktrace)
```

Spark's built-in `exception()` mapping handles this. Each exception mapped to an `(int status, ApiResponse)` pair, serialized to JSON.

## 6. Testing Strategy

- **MovieService unit tests** — test with mock `MovieRepository` (or in-memory SQLite)
- **MovieController integration tests** — start Spark on random port, hit endpoint, assert JSON structure
- **WireMock is NOT needed** here (no external HTTP calls in the web tier)
- Test cases: missing url param, empty url param, movie found, movie not found, invalid characters in url

## 7. Forward Compatibility (Exercises 4-6)

| Exercise | How this design supports it |
|----------|---------------------------|
| 4 (CacheTTL) | Inject caching wrapper into `MovieService` via constructor — no controller change |
| 5 (Guava) | Swap cache implementation behind same interface |
| 6 (Auth/Rate) | Add Spark `before()` filters — no controller change |
