# MovieVault CMS 🎬 — Movie Content Management API

[![Java](https://img.shields.io/badge/Java-17-%23ED8B00)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-C71A36)](https://maven.apache.org/)
[![SQLite](https://img.shields.io/badge/SQLite-3-003B57)](https://www.sqlite.org/)
[![Docker](https://img.shields.io/badge/Docker-ready-2496ED)](https://www.docker.com/)

A full-stack content management API for movie data — from **web crawling** and **persistent storage** to **RESTful serving** with **multi-layer caching**, **authentication**, and **rate limiting**. Deployed via Docker with SSH-based infrastructure.

> **Note:** This evolved from a single crawler assignment into a complete CMS platform across 5 progressive exercises.

## ✨ Features

### 📦 Data Acquisition (Exercise 2)
- **Web Crawler** — Scrapes ~100+ movie pages from toivote.com
- **Data Extraction** — Parses title, year, country, genres, directors, actors
- **SQLite Storage** — Persistent local database with automatic disk backup
- **Fat JAR Build** — Single executable JAR with all dependencies (`mvn package -P standalone`)

### 🌐 REST API (Exercises 3)
- **Movie Lookup** — GET endpoint returning parsed movie data in formatted JSON
- **Debug-Ready** — Conditional breakpoints configured for actor name filtering (starts with "A")

### 🚀 Caching Layer (Exercises 4-5)
- **Custom CacheTTL** (`implements Map<K,V>`)
  - Configurable TTL per entry (write expiry + read expiry)
  - Hit rate tracking
- **Guava Cache** — Production-grade replacement
  - 10s idle expiry, 20s write expiry
  - Higher throughput and thread safety

### 🔒 Security & Deployment (Exercise 6)
- **Authentication** — Login-required API access
- **Rate Limiting** — Max 2 requests/5s, 10 requests/minute per user
- **Docker Deployment** — Linux VM (Alpine) with SSH key-based auth
- **JVM Tuning** — Heap: initial 125MB, max 512MB
- **Auto-Run** — Shell script re-launches JAR every 5 seconds

## 🏗 Architecture

```
                    ┌─────────────────────────────┐
                    │      Docker / Linux VM       │
                    │  ┌───────────────────────┐  │
                    │  │   MovieVault CMS       │  │
                    │  │   (Spring Boot)        │  │
                    │  │                        │  │
                    │  │  ┌──────┐  ┌────────┐ │  │
                    │  │  │Auth  │  │ Rate   │ │  │
                    │  │  │Filter│  │ Limiter│ │  │
                    │  │  └──┬───┘  └────┬───┘ │  │
   HTTP              │  │     │          │     │  │
◄─────────────────►  │  │     └─────┬────┘     │  │
   :8080              │  │           │          │  │
                     │  │  ┌────────▼────────┐ │  │
                     │  │  │   Controller    │ │  │
                     │  │  └────────┬────────┘ │  │
                     │  │           │          │  │
                     │  │  ┌────────▼────────┐ │  │
                     │  │  │  Cache Layer     │ │  │
                     │  │  │ (TTL / Guava)   │ │  │
                     │  │  └────────┬────────┘ │  │
                     │  │           │          │  │
                     │  │  ┌────────▼────────┐ │  │
                     │  │  │     Service     │ │  │
                     │  │  └────────┬────────┘ │  │
                     │  │           │          │  │
                     │  │  ┌────────▼────────┐ │  │
                     │  │  │   Repository    │ │  │
                     │  │  │    (SQLite)     │ │  │
                     │  │  └─────────────────┘ │  │
                     │  └───────────────────────┘  │
                     └─────────────────────────────┘

  ┌──────────────┐
  │  Crawler     │      Scrapes toivote.com
  │  (Batch)     │ ──►  Parses HTML → SQLite
  └──────────────┘
```

## 🚀 Getting Started

### Prerequisites

- Java 17+
- Maven 3.9+
- Docker (optional, for deployment)

### Build

```bash
mvn clean package -P standalone
```

### Run Locally

```bash
java -jar target/movie-crawler-service-1.0.0-jar-with-dependencies.jar
```

### Docker Deploy

```bash
# Build the Docker image
docker build -t movievault-cms .

# Run container (SSH on :2222, App on :8080)
docker run -d -p 2222:22 -p 8080:8080 --name movievault movievault-cms

# Copy JAR via SCP
scp -P 2222 target/movie-crawler-service-*.jar root@localhost:/app/
```

## 📁 Project Structure

```
src/
├── main/java/com/internship/moviecrawler/
│   ├── App.java                        # Main orchestrator
│   ├── config/
│   │   └── AppConfig.java              # Configuration loading
│   ├── crawler/
│   │   ├── UrlCollector.java           # Seed URL discovery
│   │   ├── MovieFetcher.java           # HTTP client with retry
│   │   └── MovieParser.java            # HTML parsing engine
│   ├── model/
│   │   └── Movie.java                  # Movie entity
│   ├── repository/
│   │   └── SqliteMovieRepository.java  # SQLite persistence
│   ├── service/
│   │   └── ...                         # Business logic
│   └── cache/
│       ├── CacheTTL.java               # Custom TTL cache
│       └── GuavaCacheConfig.java       # Guava configuration
└── test/java/com/internship/moviecrawler/
    └── ...                             # Test suite
```

## 🔑 API Endpoints

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|:---:|
| `POST` | `/login` | Authenticate user | ❌ |
| `GET` | `/movies/{url}` | Get movie by URL | ✅ |
| `GET` | `/api/movies` | List all movies | ✅ |
| `GET` | `/prime?n=10000` | Compute & cache prime | ✅ |

## 🧪 Testing

```bash
mvn test
```

---

*Built as part of a backend engineering internship program, evolving from a web scraper into a production-ready CMS platform.*
