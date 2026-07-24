package com.internship.moviecrawler.crawler;

import com.internship.moviecrawler.model.Movie;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MovieParserTest {

    private final MovieParser parser = new MovieParser();

    // P1: Parse with all 6 fields from JSON-LD (real toivote.com mockup)
    @Test
    void parse_EnchantedFixture_ShouldExtractAllFields() throws Exception {
        String html = Files.readString(Path.of("src/test/resources/html/enchanted.html"));

        Movie movie = parser.parse(html, "https://toivote.com/movie/2d9acb2c");

        assertEquals("Phép Thuật", movie.getTitle());
        assertEquals(2007, movie.getReleaseYear());
        // country not in schema.org/Movie JSON-LD → defaults to ""
        assertEquals("", movie.getCountry());

        // Genres from JSON-LD
        assertEquals(3, movie.getGenres().size());
        assertEquals("Nhạc kịch", movie.getGenres().get(0));
        assertEquals("Viễn tưởng", movie.getGenres().get(1));
        assertEquals("Hài hước", movie.getGenres().get(2));

        // Directors from JSON-LD
        assertEquals(1, movie.getDirectors().size());
        assertEquals("Kevin Lima", movie.getDirectors().get(0));

        // Actors from JSON-LD
        assertEquals(7, movie.getActors().size());
        assertEquals("Amy Adams", movie.getActors().get(0));
        assertEquals("Julie Andrews", movie.getActors().get(6));
    }

    // P2: Minimal HTML — only title + year (fallback path, no JSON-LD)
    @Test
    void parse_MinimalHtml_ShouldHaveDefaults() throws Exception {
        String html = Files.readString(Path.of("src/test/resources/html/minimal.html"));

        Movie movie = parser.parse(html, "http://example.com/minimal");

        assertEquals("Test Movie", movie.getTitle());
        assertEquals(2023, movie.getReleaseYear());
        assertEquals("", movie.getCountry());
        assertTrue(movie.getGenres().isEmpty());
        assertTrue(movie.getDirectors().isEmpty());
        assertTrue(movie.getActors().isEmpty());
    }

    // P3: No title → ParseException
    @Test
    void parse_NoTitle_ShouldThrowParseException() throws Exception {
        String html = Files.readString(Path.of("src/test/resources/html/malformed.html"));

        assertThrows(ParseException.class,
                () -> parser.parse(html, "http://example.com/malformed"));
    }

    // P4: Null/empty HTML
    @Test
    void parse_NullHtml_ShouldThrowParseException() {
        assertThrows(ParseException.class,
                () -> parser.parse(null, "http://example.com/null"));
    }

    @Test
    void parse_EmptyHtml_ShouldThrowParseException() {
        assertThrows(ParseException.class,
                () -> parser.parse("   ", "http://example.com/blank"));
    }

    // P5: Year with mixed text
    @Test
    void parse_YearWithText_ShouldExtractNumber() throws Exception {
        String html = """
            <html><head><title>Test Film</title></head><body>
            <p class="mt-1">Năm 2023 (tái bản)</p>
            </body></html>
            """;
        Movie movie = parser.parse(html, "http://example.com/yeartest");
        assertEquals(2023, movie.getReleaseYear());
    }

    // P6: JSON-LD with genres array
    @Test
    void parse_JsonLdGenres_ShouldDeserializeArray() throws Exception {
        String html = """
            <html><head><title>Genre Test</title></head><body>
            <script type="application/ld+json">
            {"@type":"Movie","name":"Genre Test","genre":["Hành động","Tình cảm","Hài"]}
            </script>
            </body></html>
            """;
        Movie movie = parser.parse(html, "http://example.com/genretest");
        assertEquals("Genre Test", movie.getTitle());
        assertEquals(3, movie.getGenres().size());
        assertEquals("Hành động", movie.getGenres().get(0));
    }

    // P7: Single director (JSON-LD object, not array)
    @Test
    void parse_SingleDirector_ShouldReturnListOfOne() throws Exception {
        String html = """
            <html><head><title>Director Test</title></head><body>
            <script type="application/ld+json">
            {"@type":"Movie","name":"Director Test","director":{"@type":"Person","name":"James Cameron"}}
            </script>
            </body></html>
            """;
        Movie movie = parser.parse(html, "http://example.com/dirtest");
        assertEquals(1, movie.getDirectors().size());
        assertEquals("James Cameron", movie.getDirectors().get(0));
    }

    // P8: Non-UTF-8 encoding
    @Test
    void parse_Iso8859Html_ShouldParseCorrectly() throws Exception {
        String html = """
            <!DOCTYPE html><html><head>
            <meta charset="ISO-8859-1">
            <title>Café & Cinéma</title></head><body>
            <p class="mt-1">2022</p>
            </body></html>
            """;
        Movie movie = parser.parse(html, "http://example.com/encoding");
        assertEquals("Café & Cinéma", movie.getTitle());
        assertEquals(2022, movie.getReleaseYear());
    }
}
