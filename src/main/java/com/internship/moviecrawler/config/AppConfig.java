package com.internship.moviecrawler.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads and provides typed access to configuration from classpath:config.properties.
 * All values have sensible defaults so the properties file can be minimal.
 */
public class AppConfig {
    private static final String CONFIG_FILE = "config.properties";

    private final Properties props;

    public AppConfig() {
        this.props = new Properties();
        try (InputStream is = AppConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (is == null) {
                throw new RuntimeException("Config file not found on classpath: " + CONFIG_FILE);
            }
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config: " + CONFIG_FILE, e);
        }
    }

    // ---- Fetch config ----

    public long getFetchConnectTimeoutMs() {
        return Long.parseLong(props.getProperty("fetch.connect.timeout.ms", "5000"));
    }

    public long getFetchRequestTimeoutMs() {
        return Long.parseLong(props.getProperty("fetch.request.timeout.ms", "10000"));
    }

    public int getFetchMaxRetries() {
        return Integer.parseInt(props.getProperty("fetch.max.retries", "3"));
    }

    public String getFetchUserAgent() {
        return props.getProperty("fetch.user.agent", "MovieCrawler/1.0");
    }

    // ---- Crawl config ----

    public long getInterRequestDelayMs() {
        return Long.parseLong(props.getProperty("crawl.inter.request.delay.ms", "1500"));
    }

    public int getFreshnessThresholdHours() {
        return Integer.parseInt(props.getProperty("crawl.freshness.threshold.hours", "24"));
    }

    // ---- Paths ----

    public String getDbPath() {
        return props.getProperty("db.path", "data/movies.db");
    }

    public String getBackupDir() {
        return props.getProperty("backup.dir", "backup");
    }

    public String getUrlsFile() {
        return props.getProperty("urls.file", "urls.txt");
    }
}
