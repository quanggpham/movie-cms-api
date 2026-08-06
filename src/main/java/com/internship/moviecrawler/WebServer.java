package com.internship.moviecrawler;

import com.internship.moviecrawler.config.AppConfig;
import com.internship.moviecrawler.controller.AuthFilter;
import com.internship.moviecrawler.controller.MovieController;
import com.internship.moviecrawler.repository.CachedMovieRepository;
import com.internship.moviecrawler.repository.MovieRepository;
import com.internship.moviecrawler.repository.SqliteMovieRepository;
import com.internship.moviecrawler.service.MovieService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

/**
 * Entry point for the MovieVault REST API server.
 * Initializes all layers (config → repository → service → controller) via constructor DI and starts Spark.
 *
 * <p>Usage: {@code java -cp <fat-jar> com.internship.moviecrawler.WebServer}
 */
public class WebServer {

    private static final Logger log = LoggerFactory.getLogger(WebServer.class);

    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        AppConfig config = new AppConfig();

        // Init layers bottom-up with constructor dependency injection
        MovieRepository sqliteRepo = new SqliteMovieRepository(config.getDbPath());
        CachedMovieRepository cachedRepo = new CachedMovieRepository(sqliteRepo, 10, 20);
        MovieService service = new MovieService(cachedRepo);
        MovieController controller = new MovieController(service, cachedRepo);

        // Configure and start Spark
        Spark.port(DEFAULT_PORT);
        controller.registerRoutes();
        AuthFilter.register(config);

        log.info("MovieVault API started on http://localhost:{}", DEFAULT_PORT);
        log.info("Endpoints:");
        log.info("  GET /movies?url=<movie-url>  — Lookup movie by URL");
        log.info("  GET /cache/stats              — Cache hit rate & size");
    }
}
