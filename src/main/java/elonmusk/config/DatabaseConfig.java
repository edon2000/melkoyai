package elonmusk.config;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Database configuration loaded from environment variables
 * Prevents hardcoded database passwords in source code
 */
@ApplicationScoped
public class DatabaseConfig {
    
    private static final Logger LOGGER = Logger.getLogger(DatabaseConfig.class.getName());
    
    // Environment variable names
    private static final String DB_PASSWORD_ENV = "DB_PASSWORD";
    
    /**
     * Get database password from environment variable
     * @return Database password
     */
    public String getDatabasePassword() {
        String password = System.getenv(DB_PASSWORD_ENV);
        if (password == null || password.trim().isEmpty()) {
            LOGGER.severe("DB_PASSWORD environment variable is not set! Database connection will fail.");
            throw new IllegalStateException("DB_PASSWORD environment variable must be set");
        }
        return password.trim();
    }
    
    /**
     * Check if database password is configured
     * @return true if password is set
     */
    public boolean isConfigured() {
        String password = System.getenv(DB_PASSWORD_ENV);
        return password != null && !password.trim().isEmpty();
    }
}

