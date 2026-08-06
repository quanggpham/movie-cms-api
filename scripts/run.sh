#!/bin/bash
# Movie Crawler + WebServer — production runner
# Start with: nohup /opt/movie-crawler/run.sh > /opt/movie-crawler/logs/runner.log 2>&1 &

JAR_DIR="/opt/movie-crawler"
JAR_FILE="$JAR_DIR/movie-crawler-service-1.0.0-jar-with-dependencies.jar"
JVM_OPTS="-Xms125m -Xmx512m"

# Ensure CWD is $JAR_DIR so both WebServer and crawler use the same data/movies.db
cd "$JAR_DIR"

# 1. Start WebServer in background (continuous)
nohup java $JVM_OPTS -cp "$JAR_FILE" com.internship.moviecrawler.WebServer \
  > "$JAR_DIR/logs/webserver.log" 2>&1 &
echo "WebServer started with PID $! — logs: $JAR_DIR/logs/webserver.log"

# 2. Loop crawler every 5 seconds
while true; do
    echo "[$(date)] Starting crawl cycle..."
    java $JVM_OPTS -jar "$JAR_FILE"
    echo "[$(date)] Crawl cycle finished. Sleeping 5s..."
    sleep 5
done
