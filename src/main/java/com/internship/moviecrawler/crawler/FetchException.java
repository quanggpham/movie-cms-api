package com.internship.moviecrawler.crawler;

/**
 * Base exception for fetch failures.
 * Subclasses distinguish transient (retryable) from permanent (non-retryable) errors.
 */
public class FetchException extends Exception {

    public FetchException(String message) {
        super(message);
    }

    public FetchException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Retryable errors: 5xx, 408, 429, timeout, connection failure */
    public static class TransientFetchException extends FetchException {
        public TransientFetchException(String message) {
            super(message);
        }
        public TransientFetchException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Non-retryable errors: 404, 400, 403, bad Content-Type */
    public static class PermanentFetchException extends FetchException {
        public PermanentFetchException(String message) {
            super(message);
        }
        public PermanentFetchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
