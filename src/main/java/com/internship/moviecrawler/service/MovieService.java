package com.internship.moviecrawler.service;

import com.internship.moviecrawler.model.Movie;
import com.internship.moviecrawler.repository.MovieRepository;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Business logic layer for movie lookup.
 * Validates input, URL-decodes the query parameter, and queries the repository.
 */
public class MovieService {

    private final MovieRepository repo;

    public MovieService(MovieRepository repo) {
        this.repo = repo;
    }

    /**
     * Find a movie by its URL.
     *
     * @param encodedUrl the URL query parameter value (may be URL-encoded)
     * @return the matching Movie
     * @throws IllegalArgumentException if url is null or blank
     * @throws MovieNotFoundException  if no movie exists for this URL
     */
    public Movie findByUrl(String encodedUrl) {
        if (encodedUrl == null || encodedUrl.isBlank()) {
            throw new IllegalArgumentException("Query parameter 'url' is required");
        }

        String decodedUrl = URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8);

        return repo.findByUrl(decodedUrl)
                .orElseThrow(() -> new MovieNotFoundException(decodedUrl));
    }
}
