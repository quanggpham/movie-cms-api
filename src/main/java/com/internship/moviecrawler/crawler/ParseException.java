package com.internship.moviecrawler.crawler;

/**
 * Thrown when movie title cannot be extracted from HTML.
 * Other fields (year, country, etc.) failing to parse are non-fatal — they get defaults.
 */
public class ParseException extends Exception {
    public ParseException(String message) {
        super(message);
    }

    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
