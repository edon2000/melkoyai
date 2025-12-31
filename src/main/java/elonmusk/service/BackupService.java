package elonmusk.service;

import elonmusk.config.DatabaseConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.scheduler.Scheduled;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class BackupService {
    private static final Logger LOGGER = Logger.getLogger(BackupService.class.getName());
    
    private static final String BACKUP_DIR = "backups";
    private static final String DB_NAME = "elonmusk_db";
    private static final String DB_USER = "postgres";
    private static final int MAX_BACKUPS = 7;
    
    @Inject
    AuditService auditService;
    
    @Inject
    DatabaseConfig databaseConfig;
    
    @Scheduled(every = "24h")
    public void performDailyBackup() {
        try {
            createBackup("daily");
            cleanupOldBackups();
            LOGGER.info("Daily backup completed successfully");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Daily backup failed", e);
        }
    }
    
    public boolean createBackup(String type) {
        try {
            Path backupPath = Paths.get(BACKUP_DIR);
            if (!Files.exists(backupPath)) {
                Files.createDirectories(backupPath);
            }
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String backupFileName = String.format("%s_%s_%s.sql", DB_NAME, type, timestamp);
            Path backupFile = backupPath.resolve(backupFileName);
            
            ProcessBuilder processBuilder = new ProcessBuilder(
                "pg_dump",
                "-h", "localhost",
                "-U", DB_USER,
                "-d", DB_NAME,
                "-f", backupFile.toString(),
                "--verbose"
            );
            
            // Get database password from environment variable
            String dbPassword = databaseConfig.getDatabasePassword();
            processBuilder.environment().put("PGPASSWORD", dbPassword);
            
            Process process = processBuilder.start();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            
            if (finished && process.exitValue() == 0) {
                LOGGER.info("Backup created successfully: " + backupFileName);
                auditService.logAdminAction("SYSTEM", "BACKUP_CREATED", "Backup file: " + backupFileName);
                return true;
            } else {
                LOGGER.severe("Backup process failed or timed out");
                return false;
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error creating backup", e);
            return false;
        }
    }
    
    public void cleanupOldBackups() {
        try {
            Path backupPath = Paths.get(BACKUP_DIR);
            if (!Files.exists(backupPath)) {
                return;
            }
            
            Files.list(backupPath)
                .filter(path -> path.toString().endsWith(".sql"))
                .filter(path -> path.toString().contains("daily"))
                .sorted((p1, p2) -> {
                    try {
                        return Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1));
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .skip(MAX_BACKUPS)
                .forEach(path -> {
                    try {
                        Files.delete(path);
                        LOGGER.info("Deleted old backup: " + path.getFileName());
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Failed to delete old backup: " + path, e);
                    }
                });
                
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error cleaning up old backups", e);
        }
    }
}