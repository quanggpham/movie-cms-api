package com.internship.moviecrawler.controller;

import com.google.gson.Gson;
import com.internship.moviecrawler.config.AppConfig;
import com.internship.moviecrawler.dto.ApiResponse;
import spark.Spark;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Handles authentication and rate limiting via Spark before() filter.
 * <p>
 * POST /login — verify hardcoded credentials, return UUID token.
 * All other routes require Authorization: Bearer <token> header.
 */
public class AuthFilter {

    private static final Gson GSON = new Gson();
    private static final long TOKEN_TTL_HOURS = 1;

    // token → expiry epoch millis
    private static final ConcurrentHashMap<String, Long> tokens = new ConcurrentHashMap<>();

    // Rate limiting — sliding window per token
    // token → request timestamps (synchronized deque)
    private static final ConcurrentHashMap<String, SlidingWindow> rateWindows = new ConcurrentHashMap<>();

    static {
        // Clean up stale rate windows every 5 minutes
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-cleaner");
            t.setDaemon(true);
            return t;
        }).scheduleAtFixedRate(
                AuthFilter::evictStaleWindows, 5, 5, TimeUnit.MINUTES);
    }

    static {
        // Clean up expired tokens every 10 minutes
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "auth-cleaner");
            t.setDaemon(true);
            return t;
        }).scheduleAtFixedRate(
                AuthFilter::evictExpiredTokens, 10, 10, TimeUnit.MINUTES);
    }

    private AuthFilter() {} // static utility

    /**
     * Register login route and before() filter. Call once during server startup.
     */
    public static void register(AppConfig config) {
        String username = config.getAuthUsername();
        String password = config.getAuthPassword();

        // POST /login — no auth required
        Spark.post("/login", (req, res) -> {
            res.type("application/json");

            @SuppressWarnings("unchecked")
            Map<String, String> body = GSON.fromJson(req.body(), Map.class);
            String user = body != null ? body.get("username") : null;
            String pass = body != null ? body.get("password") : null;

            if (username.equals(user) && password.equals(pass)) {
                String token = UUID.randomUUID().toString();
                long expiry = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(TOKEN_TTL_HOURS);
                tokens.put(token, expiry);
                res.status(200);
                return ApiResponse.success(200, Map.of(
                        "token", token,
                        "expiresIn", TOKEN_TTL_HOURS * 3600
                )).toJson();
            }

            res.status(401);
            return ApiResponse.error(401, "UNAUTHORIZED", "Invalid credentials").toJson();
        });

        // before() filter — auth + rate limit check for all routes EXCEPT /login
        Spark.before((req, res) -> {
            if ("/login".equals(req.pathInfo()) && "POST".equalsIgnoreCase(req.requestMethod())) {
                return; // skip auth for login
            }

            // 1. Token check
            String authHeader = req.headers("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                Spark.halt(401, ApiResponse.error(401, "UNAUTHORIZED",
                        "Missing or invalid Authorization header").toJson());
            }

            String token = authHeader.substring(7);
            Long expiry = tokens.get(token);
            if (expiry == null) {
                Spark.halt(401, ApiResponse.error(401, "UNAUTHORIZED",
                        "Invalid or expired token").toJson());
            }
            if (System.currentTimeMillis() > expiry) {
                tokens.remove(token);
                Spark.halt(401, ApiResponse.error(401, "UNAUTHORIZED",
                        "Token expired").toJson());
            }

            // 2. Rate limit check
            SlidingWindow window = rateWindows.computeIfAbsent(token, k -> new SlidingWindow());
            if (!window.allow()) {
                Spark.halt(429, ApiResponse.error(429, "RATE_LIMITED",
                        "Too many requests. Limits: 2 per 5s, 10 per 60s.").toJson());
            }
        });
    }

    private static void evictExpiredTokens() {
        long now = System.currentTimeMillis();
        tokens.entrySet().removeIf(e -> e.getValue() < now);
    }

    private static void evictStaleWindows() {
        rateWindows.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    private static final class SlidingWindow {
        private static final int MAX_5S = 2;
        private static final int MAX_60S = 10;

        private final java.util.Deque<Long> timestamps = new java.util.concurrent.ConcurrentLinkedDeque<>();

        synchronized boolean allow() {
            long now = System.currentTimeMillis();
            long fiveSecAgo = now - TimeUnit.SECONDS.toMillis(5);
            long sixtySecAgo = now - TimeUnit.SECONDS.toMillis(60);

            // Evict old entries for 5s window, check
            while (!timestamps.isEmpty() && timestamps.peekFirst() < fiveSecAgo) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_5S) {
                return false; // 5s window full
            }

            // Evict old entries for 60s window, check
            while (!timestamps.isEmpty() && timestamps.peekFirst() < sixtySecAgo) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_60S) {
                return false; // 60s window full
            }

            timestamps.addLast(now);
            return true;
        }

        boolean isEmpty() {
            return timestamps.isEmpty();
        }
    }
}
