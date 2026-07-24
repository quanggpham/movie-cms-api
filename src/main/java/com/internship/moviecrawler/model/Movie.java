package com.internship.moviecrawler.model;

import java.util.Collections;
import java.util.List;

/**
 * Movie entity representing crawled data from toivote.com.
 * url is the natural key (UNIQUE in database).
 * List fields (genres, directors, actors) stored as JSON text in SQLite.
 */
public class Movie {
    private Long id;
    private String url;
    private String title;
    private Integer releaseYear;
    private String country;
    private List<String> genres;
    private List<String> directors;
    private List<String> actors;
    private String createdAt;
    private String updatedAt;
    private String lastCrawledAt;

    public Movie() {
        this.country = "";
        this.genres = Collections.emptyList();
        this.directors = Collections.emptyList();
        this.actors = Collections.emptyList();
    }

    public Movie(String url, String title, Integer releaseYear, String country,
                 List<String> genres, List<String> directors, List<String> actors) {
        this.url = url;
        this.title = title;
        this.releaseYear = releaseYear;
        this.country = country != null ? country : "";
        this.genres = genres != null ? genres : Collections.emptyList();
        this.directors = directors != null ? directors : Collections.emptyList();
        this.actors = actors != null ? actors : Collections.emptyList();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Integer getReleaseYear() { return releaseYear; }
    public void setReleaseYear(Integer releaseYear) { this.releaseYear = releaseYear; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public List<String> getGenres() { return genres; }
    public void setGenres(List<String> genres) {
        this.genres = genres != null ? genres : Collections.emptyList();
    }

    public List<String> getDirectors() { return directors; }
    public void setDirectors(List<String> directors) {
        this.directors = directors != null ? directors : Collections.emptyList();
    }

    public List<String> getActors() { return actors; }
    public void setActors(List<String> actors) {
        this.actors = actors != null ? actors : Collections.emptyList();
    }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getLastCrawledAt() { return lastCrawledAt; }
    public void setLastCrawledAt(String lastCrawledAt) { this.lastCrawledAt = lastCrawledAt; }
}
