package com.internship.moviecrawler.repository;

import com.internship.moviecrawler.model.Movie;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SqliteMovieRepositoryTest {

    private SqliteMovieRepository repo;

    @BeforeEach
    void setUp() {
        repo = new SqliteMovieRepository(":memory:");
    }

    @AfterEach
    void tearDown() {
        repo.close();
    }

    // R1: upsert() — insert new
    @Test
    void upsert_NewMovie_ShouldInsert() {
        Movie movie = new Movie("http://example.com/a", "Film A", 2023, "Vietnam",
                List.of("Hành động", "Tình cảm"), List.of("Dir A"), List.of("Actor A"));

        boolean inserted = repo.upsert(movie);

        assertTrue(inserted, "First upsert should insert");
        Optional<Movie> found = repo.findByUrl("http://example.com/a");
        assertTrue(found.isPresent());
        assertEquals("Film A", found.get().getTitle());
        assertEquals(2023, found.get().getReleaseYear());
        assertEquals(List.of("Hành động", "Tình cảm"), found.get().getGenres());
        assertEquals(List.of("Dir A"), found.get().getDirectors());
        assertEquals(List.of("Actor A"), found.get().getActors());
        assertNotNull(found.get().getCreatedAt());
        assertNotNull(found.get().getUpdatedAt());
    }

    // R2: upsert() — update existing (id and created_at preserved)
    @Test
    void upsert_ExistingUrl_ShouldUpdatePreservingId() {
        Movie first = new Movie("http://example.com/b", "Film B", 2022, "USA",
                List.of("Drama"), List.of(), List.of());
        repo.upsert(first);
        Movie firstSaved = repo.findByUrl("http://example.com/b").orElseThrow();
        Long originalId = firstSaved.getId();
        String originalCreatedAt = firstSaved.getCreatedAt();

        // Wait 1s so updated_at differs (SQLite datetime('now') is second-precision)
        try { Thread.sleep(1100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        Movie updated = new Movie("http://example.com/b", "Film B v2", 2022, "USA",
                List.of("Drama", "Action"), List.of("Dir B"), List.of("Actor B"));

        boolean inserted = repo.upsert(updated);

        assertFalse(inserted, "Second upsert should update (not insert)");
        Movie updatedSaved = repo.findByUrl("http://example.com/b").orElseThrow();
        assertEquals(originalId, updatedSaved.getId(), "id must be preserved");
        assertEquals(originalCreatedAt, updatedSaved.getCreatedAt(), "created_at must be preserved");
        assertEquals("Film B v2", updatedSaved.getTitle());
        assertEquals(List.of("Drama", "Action"), updatedSaved.getGenres());
        assertNotEquals(updatedSaved.getCreatedAt(), updatedSaved.getUpdatedAt(),
                "updated_at should differ from created_at after update");
    }

    // R3: upsert() — isolation (other rows unaffected)
    @Test
    void upsert_ShouldNotAffectOtherRows() {
        Movie a = new Movie("http://example.com/a", "Film A", 2020, "VN",
                List.of(), List.of(), List.of());
        Movie b = new Movie("http://example.com/b", "Film B", 2021, "US",
                List.of(), List.of(), List.of());
        repo.upsert(a);
        repo.upsert(b);

        Movie aUpdated = new Movie("http://example.com/a", "Film A Revised", 2020, "VN",
                List.of("Comedy"), List.of(), List.of());
        repo.upsert(aUpdated);

        Optional<Movie> bUnchanged = repo.findByUrl("http://example.com/b");
        assertTrue(bUnchanged.isPresent());
        assertEquals("Film B", bUnchanged.get().getTitle(), "Unrelated row must be untouched");
    }

    // R4: existsByUrl() — found
    @Test
    void existsByUrl_Existing_ShouldReturnTrue() {
        repo.upsert(new Movie("http://example.com/x", "X", null, "",
                List.of(), List.of(), List.of()));
        assertTrue(repo.existsByUrl("http://example.com/x"));
    }

    // R5: existsByUrl() — not found
    @Test
    void existsByUrl_NonExisting_ShouldReturnFalse() {
        assertFalse(repo.existsByUrl("http://example.com/nonexistent"));
    }

    // R6: findAll()
    @Test
    void findAll_ShouldReturnAllMovies() {
        repo.upsert(new Movie("http://example.com/a", "A", null, "",
                List.of(), List.of(), List.of()));
        repo.upsert(new Movie("http://example.com/b", "B", null, "",
                List.of(), List.of(), List.of()));
        repo.upsert(new Movie("http://example.com/c", "C", null, "",
                List.of(), List.of(), List.of()));

        List<Movie> all = repo.findAll();
        assertEquals(3, all.size());
    }

    // R7: JSON deserialization
    @Test
    void findById_ShouldDeserializeJsonLists() {
        Movie movie = new Movie("http://example.com/list", "List Test", 2023, "Korea",
                List.of("A", "B", "C"), List.of("D1", "D2"), List.of("Actor1"));
        repo.upsert(movie);

        Movie loaded = repo.findByUrl("http://example.com/list").orElseThrow();
        assertEquals(List.of("A", "B", "C"), loaded.getGenres());
        assertEquals(List.of("D1", "D2"), loaded.getDirectors());
        assertEquals(List.of("Actor1"), loaded.getActors());
    }

    // R8: Null handling in JSON columns
    @Test
    void upsert_EmptyLists_ShouldReturnEmptyLists() {
        Movie movie = new Movie("http://example.com/empty", "Empty Lists", 2023, "",
                List.of(), List.of(), List.of());
        repo.upsert(movie);

        Movie loaded = repo.findByUrl("http://example.com/empty").orElseThrow();
        assertNotNull(loaded.getGenres());
        assertNotNull(loaded.getDirectors());
        assertNotNull(loaded.getActors());
        assertTrue(loaded.getGenres().isEmpty());
        assertTrue(loaded.getDirectors().isEmpty());
        assertTrue(loaded.getActors().isEmpty());
    }
}
