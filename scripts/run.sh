#!/bin/bash
# Movie Crawler — run loop every 5 seconds
# Start with: nohup /opt/movie-crawler/run.sh > /opt/movie-crawler/logs/runner.log 2>&1 &

JAR_DIR="/opt/movie-crawler"
JAR_FILE="$JAR_DIR/movie-crawler-service-1.0.0-jar-with-dependencies.jar"

while true; do
    java -jar "$JAR_FILE"
    sleep 5
done
