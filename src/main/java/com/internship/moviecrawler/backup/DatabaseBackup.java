package com.internship.moviecrawler.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Copies the active SQLite database file to a backup directory
 * with a timestamped filename. Call AFTER repository connection is closed.
 */
public class DatabaseBackup {

    private final Path backupDir;

    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public DatabaseBackup(Path backupDir) {
        this.backupDir = backupDir;
    }

    /**
     * Copy the database file to the backup directory.
     * @param dbPath path to the active database file
     * @return path to the created backup file
     * @throws IOException if copy fails or backup directory cannot be created
     */
    public Path backup(Path dbPath) throws IOException {
        Files.createDirectories(backupDir);

        String timestamp = LocalDateTime.now().format(TS_FORMAT);
        String filename = "movies_" + timestamp + ".db";
        Path backupFile = backupDir.resolve(filename);

        Files.copy(dbPath, backupFile, StandardCopyOption.REPLACE_EXISTING);
        return backupFile;
    }
}
