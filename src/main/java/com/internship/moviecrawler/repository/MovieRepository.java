package com.internship.moviecrawler.repository;

import com.internship.moviecrawler.model.Movie;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Movie persistence.
 * Interface-first design allows swap from SQLite → in-memory mock for tests,
 * and future web service (ex3), cache (ex4-5), auth (ex6) extensions.
 */
public interface MovieRepository {

    /**
     * Insert or update a movie. Uses url as conflict key.
     * @return true if a new row was inserted, false if an existing row was updated
     */
    boolean upsert(Movie movie);

    /** Returns all movies in the database */
    List<Movie> findAll();

    /** Find by primary key id */
    Optional<Movie> findById(long id);

    /** Check if a URL already exists in the database */
    boolean existsByUrl(String url);

    /** Find by URL (natural key), returns empty if not found */
    Optional<Movie> findByUrl(String url);

    /** Close database connection */
    void close();
}
