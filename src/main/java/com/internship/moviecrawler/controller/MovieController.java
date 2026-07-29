package com.internship.moviecrawler.controller;

import com.internship.moviecrawler.dto.ApiResponse;
import com.internship.moviecrawler.model.Movie;
import com.internship.moviecrawler.service.MovieNotFoundException;
import com.internship.moviecrawler.service.MovieService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

/**
 * Registers Spark routes for the Movie REST API.
 * All responses use the standard {@link ApiResponse} envelope with HTTP status code mirrored in body.
 */
public class MovieController {

    private static final Logger log = LoggerFactory.getLogger(MovieController.class);

    private final MovieService service;

    public MovieController(MovieService service) {
        this.service = service;
    }

    /**
     * Register all routes and exception handlers with Spark.
     * Call once during server startup.
     */
    public void registerRoutes() {

        // GET /movies?url=<movie-url>
        Spark.get("/movies", (req, res) -> {
            String url = req.queryParams("url");
            Movie movie = service.findByUrl(url);

            res.status(200);
            res.type("application/json");
            return ApiResponse.success(200, movie).toJson();
        });

        // IllegalArgumentException → 400 Bad Request
        Spark.exception(IllegalArgumentException.class, (ex, req, res) -> {
            res.status(400);
            res.type("application/json");
            res.body(ApiResponse.error(400, "MISSING_PARAM", ex.getMessage()).toJson());
        });

        // MovieNotFoundException → 404 Not Found
        Spark.exception(MovieNotFoundException.class, (ex, req, res) -> {
            res.status(404);
            res.type("application/json");
            res.body(ApiResponse.error(404, "MOVIE_NOT_FOUND", ex.getMessage()).toJson());
        });

        // Catch-all → 500 Internal Server Error
        Spark.exception(Exception.class, (ex, req, res) -> {
            log.error("Unexpected error", ex);
            res.status(500);
            res.type("application/json");
            res.body(ApiResponse.error(500, "INTERNAL_ERROR", "An unexpected error occurred").toJson());
        });

        // 404 for unmatched routes (JSON, not HTML)
        Spark.notFound((req, res) -> {
            res.type("application/json");
            return ApiResponse.error(404, "NOT_FOUND",
                    "Endpoint not found: " + req.requestMethod() + " " + req.pathInfo()).toJson();
        });
    }
}
