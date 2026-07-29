package com.internship.moviecrawler.dto;

/**
 * Error payload in the API response envelope.
 *
 * @param code    machine-readable error code (e.g. "MOVIE_NOT_FOUND")
 * @param message human-readable error description
 */
public record ErrorDetail(String code, String message) {}
