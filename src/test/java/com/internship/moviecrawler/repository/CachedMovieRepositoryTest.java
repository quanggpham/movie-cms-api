package com.internship.moviecrawler.repository;

import com.internship.moviecrawler.model.Movie;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CachedMovieRepositoryTest {

    /**
     * Stub MovieRepository that counts delegate calls and returns canned data.
     */
    static class StubRepo implements MovieRepository {
        private final AtomicInteger findByUrlCalls = new AtomicInteger(0);
        private final Movie cannedMovie;

        StubRepo(Movie cannedMovie) {
            this.cannedMovie = cannedMovie;
        }

        int findByUrlCallCount() {
            return findByUrlCalls.get();
        }

        @Override
        public Optional<Movie> findByUrl(String url) {
            findByUrlCalls.incrementAndGet();
            return Optional.ofNullable(cannedMovie);
        }

        @Override public boolean upsert(Movie movie) { return true; }
        @Override public List<Movie> findAll() { return List.of(); }
        @Override public Optional<Movie> findById(long id) { return Optional.empty(); }
        @Override public boolean existsByUrl(String url) { return false; }
        @Override public void close() {}
    }

    private static final String URL = "https://toivote.com/movie/uuid-test";

    // === Cache hit/miss behavior ===

    @Test
    void findByUrl_FirstCall_ShouldHitDelegate() {
        Movie movie = new Movie(URL, "Test", 2024, "VN", List.of(), List.of(), List.of());
        StubRepo stub = new StubRepo(movie);
        CachedMovieRepository repo = new CachedMovieRepository(stub, 10, 20);

        Optional<Movie> result = repo.findByUrl(URL);

        assertTrue(result.isPresent());
        assertEquals("Test", result.get().getTitle());
        assertEquals(1, stub.findByUrlCallCount(), "First call should hit delegate");
    }

    @Test
    void findByUrl_SecondCall_ShouldHitCache() {
        Movie movie = new Movie(URL, "Test", 2024, "VN", List.of(), List.of(), List.of());
        StubRepo stub = new StubRepo(movie);
        CachedMovieRepository repo = new CachedMovieRepository(stub, 10, 20);

        repo.findByUrl(URL);   // first — hits delegate
        repo.findByUrl(URL);   // second — hits cache

        assertEquals(1, stub.findByUrlCallCount(), "Second call should NOT hit delegate");
    }

    // === Hit rate ===

    @Test
    void getHitRate_Initial_ShouldReturnZero() {
        StubRepo stub = new StubRepo(new Movie(URL, "T", null, "", List.of(), List.of(), List.of()));
        CachedMovieRepository repo = new CachedMovieRepository(stub, 10, 20);

        assertEquals(0, repo.getHitRate());
    }

    @Test
    void getHitRate_AfterHitAndMiss_ShouldBe50() {
        Movie movie = new Movie(URL, "T", null, "", List.of(), List.of(), List.of());
        StubRepo stub = new StubRepo(movie);
        CachedMovieRepository repo = new CachedMovieRepository(stub, 10, 20);

        repo.findByUrl(URL);          // miss → hit delegate
        repo.findByUrl(URL);          // hit cache
        // 1 hit / 2 requests = 50%

        assertEquals(50, repo.getHitRate());
    }

    // === Cache size ===

    @Test
    void getCacheSize_AfterPut_ShouldBeOne() {
        Movie movie = new Movie(URL, "T", null, "", List.of(), List.of(), List.of());
        StubRepo stub = new StubRepo(movie);
        CachedMovieRepository repo = new CachedMovieRepository(stub, 10, 20);

        assertEquals(0, repo.getCacheSize());
        repo.findByUrl(URL);
        assertEquals(1, repo.getCacheSize());
    }

    // === Delegating methods ===

    @Test
    void delegateMethods_ShouldPassThrough() {
        StubRepo stub = new StubRepo(null);
        CachedMovieRepository repo = new CachedMovieRepository(stub, 10, 20);

        assertTrue(repo.findAll().isEmpty());
        assertTrue(repo.findById(1L).isEmpty());
        assertFalse(repo.existsByUrl("x"));
        repo.upsert(new Movie("x", "x", null, "", List.of(), List.of(), List.of()));
        // No exception = pass
    }
}
