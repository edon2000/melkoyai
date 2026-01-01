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
            // Skip backup creation in cloud environments (Neon handles backups)
            if (isCloudEnvironment()) {
                LOGGER.info("Skipping backup creation in cloud environment - managed by Neon Database");
                return true;
            }
            
            Path backupPath = Paths.get(BACKUP_DIR);
            if (!Files.exists(backupPath)) {
                Files.createDirectories(backupPath);
            }
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String backupFileName = String.format("backup_%s_%s.sql", type, timestamp);
            Path backupFile = backupPath.resolve(backupFileName);
            
            // Extract database connection details from Quarkus configuration
            String dbUrl = databaseConfig.getDatabaseUrl();
            String dbUser = databaseConfig.getDatabaseUsername();
            String dbPassword = databaseConfig.getDatabasePassword();
            
            // Parse database URL to extract host, port, and database name
            String host = "localhost";
            String port = "5432";
            String dbName = "postgres";
            
            if (dbUrl != null && dbUrl.contains("://")) {
                try {
                    // Extract from jdbc:postgresql://host:port/database format
                    String[] parts = dbUrl.split("://")[1].split("/");
                    String hostPort = parts[0].split("\\?")[0]; // Remove query parameters
                    String[] hostPortParts = hostPort.split(":");
                    host = hostPortParts[0];
                    if (hostPortParts.length > 1) {
                        port = hostPortParts[1];
                    }
                    if (parts.length > 1) {
                        dbName = parts[1].split("\\?")[0]; // Remove query parameters
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Could not parse database URL, using defaults", e);
                }
            }
            
            ProcessBuilder processBuilder = new ProcessBuilder(
                "pg_dump",
                "-h", host,
                "-p", port,
                "-U", dbUser,
                "-d", dbName,
                "-f", backupFile.toString(),
                "--verbose"
            );
            
            // Set database password as environment variable for pg_dump
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
    
    /**
     * Check if running in a cloud environment where backups are managed externally
     */
    private boolean isCloudEnvironment() {
        String dbUrl = databaseConfig.getDatabaseUrl();
        return dbUrl != null && (dbUrl.contains("neon.tech") || dbUrl.contains("supabase.co") || 
                                dbUrl.contains("render.com") || dbUrl.contains("heroku") || 
                                dbUrl.contains("aws.com"));
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