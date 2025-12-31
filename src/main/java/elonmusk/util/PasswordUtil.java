package elonmusk.util;

import org.mindrot.jbcrypt.BCrypt;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Centralized password hashing utility using BCrypt
 * BCrypt automatically generates unique salts for each password
 */
public class PasswordUtil {
    
    private static final Logger LOGGER = Logger.getLogger(PasswordUtil.class.getName());
    private static final int BCRYPT_ROUNDS = 12; // Cost factor for BCrypt (higher = more secure but slower)
    
    /**
     * Hash a password using BCrypt with automatic salt generation
     * @param plainPassword The plain text password to hash
     * @return BCrypt hashed password
     */
    public static String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        
        try {
            String salt = BCrypt.gensalt(BCRYPT_ROUNDS);
            String hashedPassword = BCrypt.hashpw(plainPassword, salt);
            LOGGER.fine("Password hashed successfully using BCrypt");
            return hashedPassword;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error hashing password with BCrypt", e);
            throw new RuntimeException("Password hashing failed", e);
        }
    }
    
    /**
     * Verify a plain text password against a BCrypt hash
     * @param plainPassword The plain text password to verify
     * @param hashedPassword The BCrypt hashed password to verify against
     * @return true if password matches, false otherwise
     */
    public static boolean verifyPassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null) {
            return false;
        }
        
        try {
            // BCrypt.checkpw handles the salt extraction automatically
            boolean matches = BCrypt.checkpw(plainPassword, hashedPassword);
            if (!matches) {
                LOGGER.fine("Password verification failed");
            }
            return matches;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error verifying password with BCrypt", e);
            return false;
        }
    }
    
    /**
     * Check if a password hash appears to be a BCrypt hash
     * BCrypt hashes start with $2a$, $2b$, or $2y$
     * @param hash The hash to check
     * @return true if it appears to be a BCrypt hash
     */
    public static boolean isBCryptHash(String hash) {
        if (hash == null || hash.length() < 10) {
            return false;
        }
        return hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$");
    }
}

