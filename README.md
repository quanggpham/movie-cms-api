# Movie Crawler & Web Service 🎬

[![Java](https://img.shields.io/badge/Java-17-%23ED8B00)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-C71A36)](https://maven.apache.org/)
[![SQLite](https://img.shields.io/badge/SQLite-3-003B57)](https://www.sqlite.org/)
[![Spark Java](https://img.shields.io/badge/Spark--Java-2.9.4-blue)](https://sparkjava.com/)
[![Guava](https://img.shields.io/badge/Guava-33.3.0-green)](https://github.com/google/guava)
[![Docker](https://img.shields.io/badge/Docker-ready-2496ED)](https://www.docker.com/)

Hệ thống Web Crawler bóc tách dữ liệu phim từ **toivote.com**, lưu trữ vào **SQLite**, phục vụ qua **REST API** (Spark Java) với **Guava Cache**, **Authentication**, **Rate Limiting** và đóng gói triển khai bằng **Docker / Shell Script**.

---

## ✨ Features

### 📦 Crawl & SQLite Storage (Bài 2)
- **Web Crawler**: Scrape ~100+ URL phim từ toivote.com (tự động phát hiện URL từ seed pages).
- **Data Parsing**: Bóc tách tiêu đề phim, năm sản xuất, đất nước, thể loại (danh sách), đạo diễn (danh sách), diễn viên (danh sách).
- **SQLite Database**: Lưu trữ dữ liệu chuẩn hóa, tự động backup dữ liệu định kỳ ra file đĩa (`backup/movies_YYYYMMDD_HHmmss.db`).
- **Fat JAR**: Đóng gói toàn bộ project + dependencies thành 1 file JAR duy nhất (`movie-crawler-service-1.0.0-jar-with-dependencies.jar`).

### 🌐 REST Web Service (Bài 3)
- **Movie Lookup**: Endpoint `GET /movies?url=<movie-url>` trả thông tin chi tiết phim đã crawl dưới dạng JSON format đẹp (pretty-printed).

### 🚀 High Performance Caching (Bài 4 - 5)
- **Guava Cache**: Lưu cache trong bộ nhớ với chiến lược hết hạn song song:
  - Expire after access: 10 giây nếu không có request đọc.
  - Expire after write: 20 giây sau khi ghi mới.
- **Monitoring**: Endpoint `GET /cache/stats` theo dõi tỷ lệ cache hit rate và size.

### 🔒 Auth & Rate Limiting (Bài 6)
- **Authentication**: `POST /login` xác thực credentials (`admin`/`secret`), cấp Bearer Token có thời hạn.
- **Rate Limiting**: Giới hạn tối đa **2 requests / 5s** và **10 requests / 60s** per token.
- **JVM Heap Tuning**: Cấu hình khởi tạo 125MB (`-Xms125m`) và tối đa 512MB (`-Xmx512m`).
- **Docker VM & Runner Script**: Dockerfile Ubuntu 22.04 với OpenSSH key-only auth, script `run.sh` chạy REST API ngầm và lặp crawler mỗi 5s.

---

## 🏗 Architecture

```
                      ┌─────────────────────────────────┐
                      │    Docker Container / VM        │
                      │  ┌───────────────────────────┐  │
                      │  │     Spark Java WebServer  │  │
                      │  │                           │  │
                      │  │  ┌──────────┐ ┌─────────┐ │  │
                      │  │  │AuthFilter│ │RateLimit│ │  │
                      │  │  └────┬─────┘ └────┬────┘ │  │
   HTTP Request       │  │       │          │      │  │
 ───────────────────► │  │       └────┬─────┘      │  │
   :8080              │  │            │            │  │
                      │  │  ┌─────────▼──────────┐ │  │
                      │  │  │  MovieController   │ │  │
                      │  │  └─────────┬──────────┘ │  │
                      │  │            │            │  │
                      │  │  ┌─────────▼──────────┐ │  │
                      │  │  │   MovieService     │ │  │
                      │  │  └─────────┬──────────┘ │  │
                      │  │            │            │  │
                      │  │  ┌─────────▼──────────┐ │  │
                      │  │  │CachedMovieRepo     │ │  │
                      │  │  │(Guava Cache 10s/20s) │ │  │
                      │  │  └─────────┬──────────┘ │  │
                      │  │            │            │  │
                      │  │  ┌─────────▼──────────┐ │  │
                      │  │  │SqliteMovieRepo     │ │  │
                      │  │  │(data/movies.db)    │ │  │
                      │  │  └────────────────────┘ │  │
                      │  └───────────────────────────┘  │
                      └─────────────────────────────────┘

  ┌──────────────┐       Crawl toivote.com
  │   App.java   │ ────────────────────────────► HTML Parsing (Jsoup / JSON-LD)
  │ (Batch Crawl)│ ────────────────────────────► Upsert SQLite & Backup
  └──────────────┘
```

---

## 📁 Directory Structure

```
.
├── docker/
│   ├── Dockerfile                  # Ubuntu 22.04 + OpenSSH + Java 17 + Maven
│   └── authorized_keys             # SSH public key configuration
├── docs/
│   └── assignment.md               # Đề bài yêu cầu (Bài 2 -> Bài 6)
├── scripts/
│   └── run.sh                      # Shell script khởi chạy WebServer & crawl loop 5s
├── src/
│   ├── main/
│   │   ├── java/com/internship/moviecrawler/
│   │   │   ├── App.java            # Main orchestrator cho Crawler pipeline
│   │   │   ├── WebServer.java      # Main entry point cho REST Web Service (Spark)
│   │   │   ├── backup/             # Backup SQLite database
│   │   │   ├── config/             # AppConfig (đọc config.properties)
│   │   │   ├── controller/         # MovieController & AuthFilter (Spark routes & rate limit)
│   │   │   ├── crawler/            # MovieFetcher, MovieParser, UrlCollector
│   │   │   ├── dto/                # ApiResponse envelope & ErrorDetail
│   │   │   ├── model/              # Movie entity POJO
│   │   │   ├── repository/         # SqliteMovieRepository & CachedMovieRepository
│   │   │   └── service/            # MovieService business logic
│   │   └── resources/
│   │       ├── config.properties   # File cấu hình hệ thống
│   │       ├── logback.xml         # Cấu hình logging
│   │       └── urls.txt            # Danh sách URL movie mẫu
│   └── test/                       # Unit tests & Integration tests (JUnit 5 + WireMock)
├── pom.xml                         # Maven dependencies & Assembly plugin build fat JAR
└── README.md
```

---

## 🛠 Build & Run

### 1. Build Fat JAR
```bash
mvn clean package
```
*Tạo file `target/movie-crawler-service-1.0.0-jar-with-dependencies.jar`.*

### 2. Run Crawler (Bài 2)
```bash
java -jar target/movie-crawler-service-1.0.0-jar-with-dependencies.jar
```

### 3. Run REST WebServer (Bài 3 - 6)
```bash
java -cp target/movie-crawler-service-1.0.0-jar-with-dependencies.jar com.internship.moviecrawler.WebServer
```
*Server chạy tại `http://localhost:8080`.*

---

## 🔑 API Reference

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|:---:|
| `POST` | `/login` | Đăng nhập lấy Bearer token (`{"username":"admin","password":"secret"}`) | ❌ |
| `GET` | `/movies?url=<movie-url>` | Lấy thông tin phim theo URL đã crawl | ✅ Bearer |
| `GET` | `/cache/stats` | Xem thông số cache (hit rate %, size) | ✅ Bearer |

#### Response Envelope format:
```json
{
  "success": true,
  "status": 200,
  "data": {
    "url": "https://toivote.com/movie/...",
    "title": "Tên phim",
    "releaseYear": 2023,
    "country": "Mỹ",
    "genres": ["Hành động", "Phiêu lưu"],
    "directors": ["Đạo diễn A"],
    "actors": ["Diễn viên B", "Diễn viên C"]
  }
}
```

---

## 🧪 Testing

Chạy bộ test tự động (JUnit 5, WireMock, SQLite in-memory):
```bash
mvn clean test
```
