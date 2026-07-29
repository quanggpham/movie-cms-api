package com.internship.moviecrawler.service;

/**
 * Thrown when a movie URL is not found in the database.
 * Maps to HTTP 404 in the controller layer.
 */
public class MovieNotFoundException extends RuntimeException {

    private final String url;

    public MovieNotFoundException(String url) {
        super("No movie found for url: " + url);
        this.url = url;
    }

    public String getUrl() {
        return url;
    }
}
