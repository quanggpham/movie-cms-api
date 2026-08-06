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
        });
    }

    private static void evictExpiredTokens() {
        long now = System.currentTimeMillis();
        tokens.entrySet().removeIf(e -> e.getValue() < now);
    }
}
