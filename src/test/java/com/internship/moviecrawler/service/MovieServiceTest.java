package com.internship.moviecrawler.service;

import com.internship.moviecrawler.model.Movie;
import com.internship.moviecrawler.repository.SqliteMovieRepository;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MovieServiceTest {

    private SqliteMovieRepository repo;
    private MovieService service;

    private static final String TEST_URL = "https://toivote.com/movie/test-uuid";

    @BeforeEach
    void setUp() {
        repo = new SqliteMovieRepository(":memory:");
        service = new MovieService(repo);
    }

    @AfterEach
    void tearDown() {
        repo.close();
    }

    // S1: findByUrl — movie exists → returns Movie
    @Test
    void findByUrl_Existing_ShouldReturnMovie() {
        Movie saved = new Movie(TEST_URL, "Test Phim", 2024, "Vietnam",
                List.of("Hành động"), List.of("Dir A"), List.of("Actor A"));
        repo.upsert(saved);

        Movie found = service.findByUrl(TEST_URL);

        assertNotNull(found);
        assertEquals(TEST_URL, found.getUrl());
        assertEquals("Test Phim", found.getTitle());
        assertEquals(2024, found.getReleaseYear());
        assertEquals("Vietnam", found.getCountry());
        assertEquals(List.of("Hành động"), found.getGenres());
        assertEquals(List.of("Dir A"), found.getDirectors());
        assertEquals(List.of("Actor A"), found.getActors());
    }

    // S2: findByUrl — null param → IllegalArgumentException
    @Test
    void findByUrl_NullParam_ShouldThrowIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.findByUrl(null));
        assertTrue(ex.getMessage().contains("required"));
    }

    // S3: findByUrl — blank param → IllegalArgumentException
    @Test
    void findByUrl_BlankParam_ShouldThrowIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.findByUrl("   "));
        assertTrue(ex.getMessage().contains("required"));
    }

    // S4: findByUrl — non-existent URL → MovieNotFoundException
    @Test
    void findByUrl_NonExistent_ShouldThrowMovieNotFoundException() {
        MovieNotFoundException ex = assertThrows(MovieNotFoundException.class,
                () -> service.findByUrl("https://toivote.com/movie/not-found"));
        assertEquals("https://toivote.com/movie/not-found", ex.getUrl());
    }

    // S5: findByUrl — URL-encoded input → decoded and found
    @Test
    void findByUrl_UrlEncoded_ShouldDecodeAndFind() {
        Movie saved = new Movie("https://toivote.com/movie/abc def", "Space Film", 2023, "",
                List.of(), List.of(), List.of());
        repo.upsert(saved);

        // "abc def" URL-encoded = "abc%20def"
        Movie found = service.findByUrl("https://toivote.com/movie/abc%20def");

        assertNotNull(found);
        assertEquals("Space Film", found.getTitle());
    }
}
