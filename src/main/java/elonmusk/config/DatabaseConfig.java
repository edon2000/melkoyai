package elonmusk.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.logging.Logger;

/**
 * Database configuration using Quarkus configuration properties
 * Reads configuration from application.properties
 */
@ApplicationScoped
public class DatabaseConfig {
    
    private static final Logger LOGGER = Logger.getLogger(DatabaseConfig.class.getName());
    
    @ConfigProperty(name = "quarkus.datasource.password")
    String databasePassword;
    
    @ConfigProperty(name = "quarkus.datasource.username")
    String databaseUsername;
    
    @ConfigProperty(name = "quarkus.datasource.jdbc.url")
    String databaseUrl;
    
    /**
     * Get database password from Quarkus configuration
     * @return Database password
     */
    public String getDatabasePassword() {
        if (databasePassword == null || databasePassword.trim().isEmpty()) {
            LOGGER.severe("Database password is not configured in application.properties!");
            throw new IllegalStateException("Database password must be configured");
        }
        return databasePassword.trim();
    }
    
    /**
     * Get database username from Quarkus configuration
     * @return Database username
     */
    public String getDatabaseUsername() {
        return databaseUsername != null ? databaseUsername.trim() : "postgres";
    }
    
    /**
     * Get database URL from Quarkus configuration
     * @return Database URL
     */
    public String getDatabaseUrl() {
        return databaseUrl;
    }
    
    /**
     * Check if database is configured
     * @return true if password is set
     */
    public boolean isConfigured() {
        return databasePassword != null && !databasePassword.trim().isEmpty();
    }
}

