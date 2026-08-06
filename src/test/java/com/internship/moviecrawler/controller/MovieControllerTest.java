package com.internship.moviecrawler.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.internship.moviecrawler.model.Movie;
import com.internship.moviecrawler.repository.CachedMovieRepository;
import com.internship.moviecrawler.repository.SqliteMovieRepository;
import com.internship.moviecrawler.service.MovieService;
import org.junit.jupiter.api.*;
import spark.Spark;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MovieControllerTest {

    private static final int TEST_PORT = 45678;
    private static final Gson GSON = new Gson();

    private SqliteMovieRepository repo;
    private CachedMovieRepository cachedRepo;
    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        repo = new SqliteMovieRepository(":memory:");
        cachedRepo = new CachedMovieRepository(repo, 10, 20);
        MovieService service = new MovieService(cachedRepo);
        MovieController controller = new MovieController(service, cachedRepo);

        Spark.port(TEST_PORT);
        controller.registerRoutes();
        Spark.awaitInitialization();

        httpClient = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        Spark.stop();
        Spark.awaitStop();
        repo.close();
    }

    // C1: GET /movies?url=X — 200 + full JSON envelope
    @Test
    void getMovies_Existing_ShouldReturn200WithMovieData() throws Exception {
        repo.upsert(new Movie("https://toivote.com/movie/uuid-1", "Test Phim", 2024, "Vietnam",
                List.of("Hành động"), List.of("Dir X"), List.of("Actor Y")));

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + TEST_PORT + "/movies?url=https://toivote.com/movie/uuid-1"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("application/json", response.headers().firstValue("Content-Type").orElse(""));

        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
        assertTrue(json.get("success").getAsBoolean());
        assertEquals(200, json.get("status").getAsInt());
        assertFalse(json.has("error"), "error field should be absent on success");

        JsonObject data = json.getAsJsonObject("data");
        assertEquals("Test Phim", data.get("title").getAsString());
        assertEquals(2024, data.get("releaseYear").getAsInt());
        assertEquals("Vietnam", data.get("country").getAsString());
        assertNotNull(data.get("createdAt"));
    }

    // C2: GET /movies (no param) → 400 + error envelope
    @Test
    void getMovies_MissingParam_ShouldReturn400() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + TEST_PORT + "/movies"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());

        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
        assertFalse(json.get("success").getAsBoolean());
        assertEquals(400, json.get("status").getAsInt());
        assertFalse(json.has("data"), "data field should be absent on error");

        JsonObject error = json.getAsJsonObject("error");
        assertEquals("MISSING_PARAM", error.get("code").getAsString());
        assertTrue(error.get("message").getAsString().contains("required"));
    }

    // C3: GET /movies?url=NOTFOUND → 404 + error envelope
    @Test
    void getMovies_NotFound_ShouldReturn404() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + TEST_PORT + "/movies?url=https://toivote.com/movie/no-such-uuid"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());

        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
        assertFalse(json.get("success").getAsBoolean());
        assertEquals(404, json.get("status").getAsInt());

        JsonObject error = json.getAsJsonObject("error");
        assertEquals("MOVIE_NOT_FOUND", error.get("code").getAsString());
    }

    // C4: Pretty-print verification — response body contains indented JSON
    @Test
    void getMovies_Existing_ShouldReturnPrettyPrintedJson() throws Exception {
        repo.upsert(new Movie("https://toivote.com/movie/uuid-pretty", "Pretty", null, "",
                List.of(), List.of(), List.of()));

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + TEST_PORT + "/movies?url=https://toivote.com/movie/uuid-pretty"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        String body = response.body();
        assertTrue(body.contains("\n"), "Response should be pretty-printed (contain newlines)");
        assertTrue(body.contains("  "), "Response should contain 2-space indent");
    }
}
