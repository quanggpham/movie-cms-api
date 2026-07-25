package com.internship.moviecrawler.repository;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.internship.moviecrawler.model.Movie;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/**
 * SQLite implementation of MovieRepository.
 * Handles schema creation, upsert with ON CONFLICT ... DO UPDATE,
 * and JSON column serialization/deserialization via Gson.
 */
public class SqliteMovieRepository implements MovieRepository {
    private static final Type LIST_STRING_TYPE = new TypeToken<List<String>>(){}.getType();

    private final Connection conn;
    private final Gson gson;

    private static final String CREATE_TABLE = """
        CREATE TABLE IF NOT EXISTS movies (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            url             TEXT NOT NULL UNIQUE,
            title           TEXT NOT NULL,
            release_year    INTEGER,
            country         TEXT DEFAULT '',
            genres          TEXT DEFAULT '[]',
            directors       TEXT DEFAULT '[]',
            actors          TEXT DEFAULT '[]',
            created_at      TEXT DEFAULT (datetime('now')),
            updated_at      TEXT DEFAULT (datetime('now')),
            last_crawled_at TEXT DEFAULT (datetime('now'))
        )
        """;

    private static final String UPSERT_SQL = """
        INSERT INTO movies (url, title, release_year, country, genres, directors, actors, last_crawled_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, datetime('now'))
        ON CONFLICT(url) DO UPDATE SET
            title           = excluded.title,
            release_year    = excluded.release_year,
            country         = excluded.country,
            genres          = excluded.genres,
            directors       = excluded.directors,
            actors          = excluded.actors,
            updated_at      = datetime('now'),
            last_crawled_at = datetime('now')
        """;

    private static final String SELECT_ALL     = "SELECT * FROM movies";
    private static final String SELECT_BY_ID   = "SELECT * FROM movies WHERE id = ?";
    private static final String SELECT_BY_URL  = "SELECT * FROM movies WHERE url = ?";
    private static final String EXISTS_BY_URL  = "SELECT COUNT(*) FROM movies WHERE url = ?";

    public SqliteMovieRepository(String dbPath) {
        this.gson = new Gson();
        try {
            Class.forName("org.sqlite.JDBC");
            // Ensure parent directories exist (skip for in-memory / special paths)
            if (!dbPath.startsWith(":")) {
                Path dbFile = Path.of(dbPath);
                if (dbFile.getParent() != null) {
                    Files.createDirectories(dbFile.getParent());
                }
            }
            this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            initSchema();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize SQLite database: " + dbPath, e);
        }
    }

    private void initSchema() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_TABLE);
        }
    }

    @Override
    public boolean upsert(Movie movie) {
        try {
            boolean exists = existsByUrl(movie.getUrl());

            try (PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {
                ps.setString(1, movie.getUrl());
                ps.setString(2, movie.getTitle());
                if (movie.getReleaseYear() != null) {
                    ps.setInt(3, movie.getReleaseYear());
                } else {
                    ps.setNull(3, Types.INTEGER);
                }
                ps.setString(4, movie.getCountry());
                ps.setString(5, gson.toJson(movie.getGenres()));
                ps.setString(6, gson.toJson(movie.getDirectors()));
                ps.setString(7, gson.toJson(movie.getActors()));
                ps.executeUpdate();
            }

            return !exists; // true = new insert, false = update
        } catch (SQLException e) {
            throw new RuntimeException("Failed to upsert movie: " + movie.getUrl(), e);
        }
    }

    @Override
    public List<Movie> findAll() {
        List<Movie> movies = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_ALL)) {
            while (rs.next()) {
                movies.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch all movies", e);
        }
        return movies;
    }

    @Override
    public Optional<Movie> findById(long id) {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find movie by id: " + id, e);
        }
        return Optional.empty();
    }

    @Override
    public boolean existsByUrl(String url) {
        try (PreparedStatement ps = conn.prepareStatement(EXISTS_BY_URL)) {
            ps.setString(1, url);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check URL existence: " + url, e);
        }
    }

    @Override
    public Optional<Movie> findByUrl(String url) {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_BY_URL)) {
            ps.setString(1, url);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find movie by url: " + url, e);
        }
        return Optional.empty();
    }

    @Override
    public void close() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to close database connection", e);
        }
    }

    private Movie mapRow(ResultSet rs) throws SQLException {
        Movie m = new Movie();
        m.setId(rs.getLong("id"));
        m.setUrl(rs.getString("url"));
        m.setTitle(rs.getString("title"));
        m.setReleaseYear(rs.getObject("release_year") != null ? rs.getInt("release_year") : null);
        m.setCountry(rs.getString("country"));
        m.setGenres(deserializeList(rs.getString("genres")));
        m.setDirectors(deserializeList(rs.getString("directors")));
        m.setActors(deserializeList(rs.getString("actors")));
        m.setCreatedAt(rs.getString("created_at"));
        m.setUpdatedAt(rs.getString("updated_at"));
        m.setLastCrawledAt(rs.getString("last_crawled_at"));
        return m;
    }

    private List<String> deserializeList(String json) {
        if (json == null || json.isEmpty() || "null".equals(json)) {
            return Collections.emptyList();
        }
        return gson.fromJson(json, LIST_STRING_TYPE);
    }
}
