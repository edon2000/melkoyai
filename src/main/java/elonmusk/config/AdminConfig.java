package elonmusk.config;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Admin configuration loaded from environment variables
 * Prevents hardcoded credentials in source code
 */
@ApplicationScoped
public class AdminConfig {
    
    private static final Logger LOGGER = Logger.getLogger(AdminConfig.class.getName());
    
    // Environment variable names
    private static final String ADMIN_USERNAME_ENV = "ADMIN_USERNAME";
    private static final String ADMIN_PASSWORD_ENV = "ADMIN_PASSWORD";
    
    // Default values for development (should NOT be used in production)
    private static final String DEFAULT_ADMIN_USERNAME = "treader";
    private static final String DEFAULT_ADMIN_PASSWORD = "800111";
    
    /**
     * Get admin username from environment variable
     * @return Admin username
     */
    public String getAdminUsername() {
        String username = System.getenv(ADMIN_USERNAME_ENV);
        if (username == null || username.trim().isEmpty()) {
            LOGGER.warning("ADMIN_USERNAME environment variable not set, using default (NOT SECURE FOR PRODUCTION)");
            return DEFAULT_ADMIN_USERNAME;
        }
        return username.trim();
    }
    
    /**
     * Get admin password from environment variable
     * @return Admin password
     */
    public String getAdminPassword() {
        String password = System.getenv(ADMIN_PASSWORD_ENV);
        if (password == null || password.trim().isEmpty()) {
            LOGGER.warning("ADMIN_PASSWORD environment variable not set, using default (NOT SECURE FOR PRODUCTION)");
            return DEFAULT_ADMIN_PASSWORD;
        }
        return password.trim();
    }
    
    /**
     * Validate admin credentials
     * @param username The username to validate
     * @param password The password to validate
     * @return true if credentials match
     */
    public boolean validateCredentials(String username, String password) {
        if (username == null || password == null) {
            return false;
        }
        
        String expectedUsername = getAdminUsername();
        String expectedPassword = getAdminPassword();
        
        return expectedUsername.equals(username.trim()) && expectedPassword.equals(password.trim());
    }
    
    /**
     * Check if environment variables are properly configured
     * @return true if environment variables are set (not using defaults)
     */
    public boolean isConfigured() {
        String username = System.getenv(ADMIN_USERNAME_ENV);
        String password = System.getenv(ADMIN_PASSWORD_ENV);
        return username != null && !username.trim().isEmpty() && 
               password != null && !password.trim().isEmpty();
    }
}

