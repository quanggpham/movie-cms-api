package com.internship.moviecrawler.repository;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.internship.moviecrawler.model.Movie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class CachedMovieRepository implements MovieRepository {
    private static final Logger log = LoggerFactory.getLogger(CachedMovieRepository.class);

    private final MovieRepository delegate;
    private final Cache<String, Movie> cache;

    public CachedMovieRepository(MovieRepository delegate, int idleSeconds, int writeSeconds) {
        this.delegate = delegate;
        this.cache = CacheBuilder.newBuilder()
                .expireAfterAccess(idleSeconds, TimeUnit.SECONDS)
                .expireAfterWrite(writeSeconds, TimeUnit.SECONDS)
                .recordStats()
                .build();
    }

    @Override
    public Optional<Movie> findByUrl(String url) {
        Movie cached = cache.getIfPresent(url);
        if (cached != null) {
            log.info("💚 CACHE HIT  — {}", url);
            return Optional.of(cached);
        }

        log.info("💔 CACHE MISS — {} — querying DB...", url);
        Optional<Movie> movie = delegate.findByUrl(url);

        movie.ifPresent(m -> {
            log.info("📦 CACHE PUT  — {}", url);
            cache.put(url, m);
        });

        return movie;
    }

    @Override
    public boolean upsert(Movie movie)                { return delegate.upsert(movie); }
    @Override
    public List<Movie> findAll()                      { return delegate.findAll(); }
    @Override
    public Optional<Movie> findById(long id)           { return delegate.findById(id); }
    @Override
    public boolean existsByUrl(String url)             { return delegate.existsByUrl(url); }
    @Override
    public void close()                               { delegate.close(); }

    /** Expose hit rate from the underlying cache (for monitoring / exercise 4). */
    public int getHitRate() {
        return (int) (cache.stats().hitRate() * 100);
    }

    /** Expose current cache size (including expired entries not yet evicted). */
    public int getCacheSize()                         { return (int) cache.size(); }
}
