# Movie CMS — Deploy & Verification Guide (Local)

> **Local copy** cho máy `Admin` — path SSH key là `C:\Users\Admin\.ssh\moviebot_key`,
> project ở `F:\App\hoc\workspace\VCCORP\projects\movie-cms-api`.
> File gốc: `docs/deploy-guide.md` (dành cho máy `Quang`).

Hướng dẫn triển khai toàn bộ project (Bài 2-6) lên Docker container, kèm cách kiểm tra từng yêu cầu.

---

## Mục lục

1. [Chuẩn bị SSH Key](#1-chuẩn-bị-ssh-key)
2. [Build Docker Image](#2-build-docker-image)
3. [Khởi động Container](#3-khởi-động-container)
4. [SSH vào Container](#4-ssh-vào-container)
5. [Deploy Project](#5-deploy-project)
6. [Build & Chạy](#6-build--chạy)
7. [Verification — Kiểm tra từng bài](#7-verification--kiểm-tra-từng-bài)
8. [Xem Log](#8-xem-log)
9. [Troubleshooting](#9-troubleshooting)

---

## 1. Chuẩn bị SSH Key

### 1.1. Tạo SSH key (nếu chưa có)

```powershell
# Trên máy Windows (PowerShell)
ssh-keygen -t rsa -b 4096 -f C:\Users\Admin\.ssh\moviebot_key -N "" -C "moviebot@docker"
```

Kết quả tạo ra 2 file:
- `C:\Users\Admin\.ssh\moviebot_key` — private key (giữ trên máy)
- `C:\Users\Admin\.ssh\moviebot_key.pub` — public key (copy vào Docker)

### 1.2. Copy public key vào authorized_keys

```powershell
Copy-Item C:\Users\Admin\.ssh\moviebot_key.pub docker\authorized_keys
```

> File `docker/authorized_keys` sẽ được Dockerfile copy vào container tại `/home/moviebot/.ssh/authorized_keys`

---

## 2. Build Docker Image

```bash
# Trong thư mục project gốc
cd F:\App\hoc\workspace\VCCORP\projects\movie-cms-api
docker build -t movie-cms -f docker/Dockerfile .
```

**Kiểm tra build thành công:**
```bash
docker images movie-cms
```
Expected: thấy image `movie-cms` với tag `latest`

---

## 3. Khởi động Container

```bash
docker run -d --name movie-cms -p 2222:22 -p 8080:8080 movie-cms
```

Giải thích:
- `-d` — chạy nền (detached)
- `--name movie-cms` — đặt tên container
- `-p 2222:22` — map port SSH (máy thật:2222 → container:22)
- `-p 8080:8080` — map port API (máy thật:8080 → container:8080)

**Kiểm tra container đang chạy:**
```bash
docker ps
```
Expected: thấy container `movie-cms` status `Up`

---

## 4. SSH vào Container

```bash
ssh -i C:\Users\Admin\.ssh\moviebot_key -p 2222 moviebot@localhost
```

**Kiểm tra kết nối thành công:**
```bash
whoami        # Expected: moviebot
java -version # Expected: openjdk 17
mvn --version # Expected: Maven 3.x
```

---

## 5. Deploy Project

Trong SSH session trên container:

```bash
# Clone repo
cd /opt/movie-crawler
git clone https://github.com/quanggpham/movie-cms-api.git source

# Verify
ls source/
# Expected: pom.xml, src/, scripts/, docker/, docs/, ...
```

---

## 6. Build & Chạy

```bash
cd /opt/movie-crawler/source

# Build fat JAR
mvn clean package

# Copy JAR ra thư mục app
cp target/movie-crawler-service-*-jar-with-dependencies.jar /opt/movie-crawler/

# Copy run.sh
cp scripts/run.sh /opt/movie-crawler/
chmod +x /opt/movie-crawler/run.sh

# Chạy (run.sh tự cd vào $JAR_DIR trước khi start để đảm bảo WebServer và crawler dùng chung data/movies.db)
nohup /opt/movie-crawler/run.sh > /opt/movie-crawler/logs/runner.log 2>&1 &

# Kiểm tra process — phải thấy 2 java process
ps aux | grep java
```

**Dấu hiệu thành công:**
- `ps aux | grep java` thấy 2 Java process: 1 WebServer, 1 crawler, cả 2 đều có `-Xms125m -Xmx512m`
- Cả 2 process phải có CWD = `/opt/movie-crawler` (kiểm tra: `ls -la /proc/<PID>/cwd`)
- `tail -f /opt/movie-crawler/logs/webserver.log` thấy `MovieVault API started on http://localhost:8080`

---

## 7. Verification — Kiểm tra từng bài

> Chạy các lệnh sau **trên máy thật** (Windows), vì container map port 8080 ra localhost:8080.

### 7.1. Bài 2 — Web Crawler & SQLite

**Kiểm tra DB và urls.txt tồn tại:**

```bash
ssh -i C:/Users/Admin/.ssh/moviebot_key -p 2222 moviebot@localhost "ls -la /opt/movie-crawler/data/"
```
Expected: thấy `movies.db` và `urls.txt` (file URLs auto-discovered từ UrlCollector)

**Kiểm tra backup:**

```bash
ssh -i C:/Users/Admin/.ssh/moviebot_key -p 2222 moviebot@localhost "ls -la /opt/movie-crawler/backup/"
```
Expected: thấy file backup `movies_YYYYMMDD_HHmmss.db`

**Kiểm tra dữ liệu trong DB:**

```bash
ssh -i C:/Users/Admin/.ssh/moviebot_key -p 2222 moviebot@localhost "cat > /tmp/QueryDb.java << 'JAVAEOF'
import java.sql.*;
public class QueryDb {
    public static void main(String[] args) throws Exception {
        Class.forName(\"org.sqlite.JDBC\");
        try (Connection c = DriverManager.getConnection(\"jdbc:sqlite:/opt/movie-crawler/data/movies.db\");
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(\"SELECT url, title FROM movies LIMIT 3\")) {
            while (rs.next()) System.out.println(rs.getString(\"url\") + \" | \" + rs.getString(\"title\"));
        }
    }
}
JAVAEOF
cd /tmp && javac QueryDb.java && java -cp /opt/movie-crawler/movie-crawler-service-*-jar-with-dependencies.jar:. QueryDb"
```

**Kiểm tra crawler log:**

```bash
ssh -i C:/Users/Admin/.ssh/moviebot_key -p 2222 moviebot@localhost "tail -20 /opt/movie-crawler/logs/runner.log"
```
Expected: thấy `Starting crawl cycle...` và `Crawl cycle finished. Sleeping 5s...`

### 7.2. Bài 3 — REST Web Service

```bash
# Lấy URL movie có sẵn từ data/urls.txt (trên container)
ssh -i C:/Users/Admin/.ssh/moviebot_key -p 2222 moviebot@localhost "grep '^https://' /opt/movie-crawler/data/urls.txt | head -3"

# Hoặc lấy URL từ DB (dùng câu lệnh Java ở trên)
# Dùng URL đó để test (thay bằng URL thực tế từ DB)
TOKEN=$(curl -s -X POST http://localhost:8080/login -H "Content-Type: application/json" -d '{"username":"admin","password":"secret"}' | sed -n 's/.*"token": "\([^"]*\)".*/\1/p')

curl -s "http://localhost:8080/movies?url=URL_CUA_BAN" \
  -H "Authorization: Bearer $TOKEN" | head -15
```
Expected:
```json
{
  "success": true,
  "status": 200,
  "data": {
    "id": 1,
    "url": "...",
    "title": "Bài Học Đáng Đời",
    ...
  }
}
```

**Kiểm tra lỗi 404:**
```bash
curl -s http://localhost:8080/movies?url=https://toivote.com/movie/not-exists \
  -H "Authorization: Bearer $TOKEN"
```
Expected: `{"success":false,"status":404,"error":{"code":"MOVIE_NOT_FOUND",...}}`

**Kiểm tra lỗi 401 (không token):**
```bash
curl -s http://localhost:8080/movies?url=https://toivote.com/movie/test
```
Expected: `{"success":false,"status":401,"error":{"code":"UNAUTHORIZED",...}}`

### 7.3. Bài 4 — Custom CacheTTL (đã thay thế bởi Guava)

> Bài 4 được xác nhận qua sự tồn tại của `/cache/stats` endpoint và hit rate reporting.

### 7.4. Bài 5 — Guava Cache

**Test cache hit:**
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/login -H "Content-Type: application/json" -d '{"username":"admin","password":"secret"}' | sed -n 's/.*"token": "\([^"]*\)".*/\1/p')

# Query lần 1 — MISS, hitRate=0
curl -s http://localhost:8080/cache/stats -H "Authorization: Bearer $TOKEN"
# → "hitRate": 0

# Query phim lần 1
URL="<URL-TU-DB-CUA-BAN>"
# Ví dụ: URL="https://toivote.com/movie/042dba84-697b-4f41-92a7-4ce7739ebee4"
curl -s "http://localhost:8080/movies?url=$URL" -H "Authorization: Bearer $TOKEN" > /dev/null

# Query phim lần 2 — HIT từ cache
curl -s "http://localhost:8080/movies?url=$URL" -H "Authorization: Bearer $TOKEN" > /dev/null

# Xem stats — hitRate > 0
curl -s http://localhost:8080/cache/stats -H "Authorization: Bearer $TOKEN"
# → "hitRate" > 0, "cacheSize": 1
```

**Test TTL idle (10s):**
```bash
# Đợi 12 giây
sleep 12

# Query lại — MISS (hitRate vẫn tăng nhưng cache là miss)
curl -s "http://localhost:8080/movies?url=$URL" -H "Authorization: Bearer $TOKEN" > /dev/null
curl -s http://localhost:8080/cache/stats -H "Authorization: Bearer $TOKEN"
```

**Dấu hiệu thành công:**
- `hitRate` tăng dần sau mỗi cache hit
- Sau 12s idle, query lại là cache MISS (phải đọc DB)
- `cacheSize` = 1 (entry vẫn được cache lại)

> ✅ Kiểm tra bổ sung: `mvn test` trên container — 37 tests, 0 failures

### 7.5. Bài 5 — Git Conflict Resolution

**Kiểm tra git log có merge commit:**

```bash
ssh -i C:/Users/Admin/.ssh/moviebot_key -p 2222 moviebot@localhost \
  "cd /opt/movie-crawler/source && git log --oneline --graph -10"
```
Expected: thấy merge commit `merge: resolve conflict — hop nhat comment server & client`

**Kiểm tra file đã resolve:**
```bash
ssh -i C:/Users/Admin/.ssh/moviebot_key -p 2222 moviebot@localhost \
  "grep 'Chinh sua hop nhat' /opt/movie-crawler/source/src/main/java/com/internship/moviecrawler/repository/CachedMovieRepository.java"
```
Expected: `/* Chinh sua hop nhat giua server & client */`

### 7.6. Bài 6 — Auth & Rate Limiting

**Test Auth:**

```bash
# 1. Login thành công
curl -s -X POST http://localhost:8080/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"secret"}'
# Expected: {"success":true,"data":{"token":"...","expiresIn":3600}}

# 2. Login sai password
curl -s -X POST http://localhost:8080/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"wrong"}'
# Expected: {"success":false,"status":401,"error":{"code":"UNAUTHORIZED",...}}

# 3. API không có token
curl -s http://localhost:8080/cache/stats
# Expected: 401 UNAUTHORIZED

# 4. Token sai
curl -s http://localhost:8080/cache/stats -H "Authorization: Bearer fake-token"
# Expected: 401 UNAUTHORIZED

# 5. Token đúng
TOKEN=$(curl -s -X POST http://localhost:8080/login -H "Content-Type: application/json" -d '{"username":"admin","password":"secret"}' | sed -n 's/.*"token": "\([^"]*\)".*/\1/p')
curl -s http://localhost:8080/cache/stats -H "Authorization: Bearer $TOKEN"
# Expected: 200 {"success":true,...}
```

**Test Rate Limiting (5s window):**

```bash
# 3 requests liên tiếp
for i in 1 2 3; do
  echo "Request $i:"
  curl -s http://localhost:8080/cache/stats -H "Authorization: Bearer $TOKEN" | grep '"status"'
done
# Expected:
# Request 1: "status": 200
# Request 2: "status": 200
# Request 3: "status": 429  ← RATE_LIMITED

# Đợi 6 giây — window reset
sleep 6
curl -s http://localhost:8080/cache/stats -H "Authorization: Bearer $TOKEN" | grep '"status"'
# Expected: "status": 200
```

**Test Rate Limiting (60s window):**

```bash
# Gửi 11 requests (cách nhau 3s mỗi 2 cái để không bị chặn 5s)
for batch in 1 2 3 4 5; do
  TOKEN=$(curl -s -X POST http://localhost:8080/login -H "Content-Type: application/json" -d '{"username":"admin","password":"secret"}' | sed -n 's/.*"token": "\([^"]*\)".*/\1/p')
  for i in 1 2; do
    curl -s http://localhost:8080/cache/stats -H "Authorization: Bearer $TOKEN" > /dev/null
  done
  sleep 3
  # Request thứ 11 sẽ bị 429 nếu vẫn trong 60s
done
```

### 7.7. Bài 6 — JVM Heap

```bash
ssh -i C:/Users/Admin/.ssh/moviebot_key -p 2222 moviebot@localhost "ps aux | grep java"
```
Expected: trong output thấy `-Xms125m -Xmx512m`

---

## 8. Xem Log

```bash
# Log WebServer (API requests, cache hits/misses)
ssh -i C:/Users/Admin/.ssh/moviebot_key -p 2222 moviebot@localhost \
  "tail -f /opt/movie-crawler/logs/webserver.log"

# Log Crawler (crawl cycle)
ssh -i C:/Users/Admin/.ssh/moviebot_key -p 2222 moviebot@localhost \
  "tail -f /opt/movie-crawler/logs/runner.log"

# Log ứng dụng (logback — trong source/logs)
ssh -i C:/Users/Admin/.ssh/moviebot_key -p 2222 moviebot@localhost \
  "tail -f /opt/movie-crawler/source/logs/movie-crawler.log"
```

---

## 9. Troubleshooting

| Vấn đề | Nguyên nhân | Cách fix |
|--------|-------------|----------|
| Port 8080 already in use | Process cũ chưa tắt | `docker restart movie-cms` hoặc kill process java |
| Container không start | Dockerfile build lỗi | `docker logs movie-cms` |
| SSH "Permission denied" | Key sai hoặc chưa copy | Kiểm tra `docker/authorized_keys` đúng nội dung public key |
| Maven build lỗi | Thiếu JDK | Container cần `openjdk-17-jdk-headless` (đã có trong Dockerfile mới) |
| `movies.db` trống | Chưa chạy crawler hoặc freshness < 24h | Đợi crawler loop 5s hoặc xóa `data/movies.db` trước khi chạy |
| `mvn test` fail | File lock hoặc port conflict | Chạy test trước khi start WebServer, hoặc dùng port test khác |
| API luôn 404 dù DB có dữ liệu | WebServer và crawler dùng CWD khác nhau → đọc 2 file `movies.db` khác nhau | Kiểm tra CWD: `ls -la /proc/<PID>/cwd`. Cả 2 phải trỏ đến `/opt/movie-crawler`. Nếu không khớp, `run.sh` phải có `cd "$JAR_DIR"` trước khi start. |
| `data/urls.txt` không tồn tại | Code cũ dùng relative path `Path.of(dbPath).getParent().resolve("urls.txt")` | Commit `899d670` đã fix bằng `toAbsolutePath()`. Pull code mới, rebuild. |
| `mvn clean package -P standalone` báo profile not found | pom.xml không có profile `standalone` | Dùng `mvn clean package` (không profile). Assembly plugin tự chạy ở phase package. |
| `grep -oP` lỗi "supports only unibyte and UTF-8 locales" | Container không có GNU grep PCRE | Dùng `sed -n 's/.*"token": "\([^"]*\)".*/\1/p'` thay thế. |
| Cả 2 process CWD đều đúng nhưng API vẫn 404 | DB crawl đã cũ và bị freshness check skip | Đợi 24h cho freshness expire, hoặc xóa DB cũ và chạy lại crawler. |
