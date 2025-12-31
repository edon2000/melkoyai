package elonmusk.filter;

import elonmusk.util.PasswordUtil;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Security utility for filter package
 * Delegates password operations to centralized PasswordUtil
 * This class is deprecated - use elonmusk.util.SecurityUtil instead
 */
@Deprecated
public class SecurityUtil {
    
    private static final Logger LOGGER = Logger.getLogger(SecurityUtil.class.getName());
    private static final SecureRandom RANDOM = new SecureRandom();
    
    /**
     * Hash a password using BCrypt (delegates to PasswordUtil)
     * @param password The plain text password to hash
     * @return BCrypt hashed password
     */
    public static String hashPassword(String password) {
        return PasswordUtil.hashPassword(password);
    }
    
    /**
     * Verify a plain text password against a BCrypt hash (delegates to PasswordUtil)
     * @param plainPassword The plain text password to verify
     * @param hashedPassword The BCrypt hashed password to verify against
     * @return true if password matches, false otherwise
     */
    public static boolean verifyPassword(String plainPassword, String hashedPassword) {
        return PasswordUtil.verifyPassword(plainPassword, hashedPassword);
    }
    
    public static String generateReferralCode(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 9) {
            throw new IllegalArgumentException("Phone number must be 10 characters");
        }
        
        try {
            String base = phoneNumber.substring(phoneNumber.length() - 4);
            int randomNum = RANDOM.nextInt(9000) + 1000;
            return "REF" + base + randomNum;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error generating referral code", e);
            String hash = Integer.toHexString(phoneNumber.hashCode()).toUpperCase();
            return "REF" + hash.substring(0, Math.min(6, hash.length()));
        }
    }
    
    public static String sanitizeInput(String input) {
        if (input == null) return null;
        
        try {
            return input.trim()
                       .replaceAll("<", "&lt;")
                       .replaceAll(">", "&gt;")
                       .replaceAll("\"", "&quot;")
                       .replaceAll("'", "&#x27;")
                       .replaceAll("/", "&#x2F;")
                       .replaceAll("&", "&amp;")
                       .replaceAll("\\(", "&#40;")
                       .replaceAll("\\)", "&#41;")
                       .replaceAll("\\{", "&#123;")
                       .replaceAll("\\}", "&#125;")
                       .replaceAll("\\[", "&#91;")
                       .replaceAll("\\]", "&#93;");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error sanitizing input", e);
            return input != null ? input.trim() : null;
        }
    }
    
    public static boolean isValidInput(String input, int maxLength) {
        if (input == null) return false;
        if (input.trim().isEmpty()) return false;
        if (input.length() > maxLength) return false;
        
        String[] maliciousPatterns = {
            "<script", "javascript:", "onload=", "onerror=", "onclick=",
            "drop table", "delete from", "insert into", "update set",
            "union select", "or 1=1", "' or '", "\" or \"",
            "exec(", "eval(", "system(", "cmd(", "shell("
        };
        
        String lowerInput = input.toLowerCase();
        for (String pattern : maliciousPatterns) {
            if (lowerInput.contains(pattern.toLowerCase())) {
                LOGGER.warning("Potentially malicious input detected: " + pattern);
                return false;
            }
        }
        
        return true;
    }
    
    public static boolean isValidSession(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return false;
        }
        
        return sessionId.length() >= 10 && sessionId.length() <= 128 && 
               sessionId.matches("^[a-zA-Z0-9_-]+$");
    }
    
    public static String generateSecureToken() {
        try {
            byte[] randomBytes = new byte[32];
            RANDOM.nextBytes(randomBytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error generating secure token", e);
            return String.valueOf(System.currentTimeMillis()) + RANDOM.nextInt(10000);
        }
    }
    
    public static boolean isValidAmount(String amountStr) {
        if (amountStr == null || amountStr.trim().isEmpty()) {
            return false;
        }
        
        try {
            java.math.BigDecimal amount = new java.math.BigDecimal(amountStr);
            return amount.compareTo(java.math.BigDecimal.ZERO) > 0 && 
                   amount.compareTo(new java.math.BigDecimal("10000000")) <= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}