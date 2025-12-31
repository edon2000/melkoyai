package elonmusk.util;

import java.security.SecureRandom;
import java.util.logging.Logger;

public class TokenGenerator {
    
    private static final Logger LOGGER = Logger.getLogger(TokenGenerator.class.getName());
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    
    /**
     * Generate a secure 8-character gift code using uppercase letters and numbers only
     * Format: ABC123XY (exactly 8 characters)
     */
    public static String generateGiftCode() {
        StringBuilder code = new StringBuilder(8);
        
        for (int i = 0; i < 8; i++) {
            int index = RANDOM.nextInt(CHARACTERS.length());
            code.append(CHARACTERS.charAt(index));
        }
        
        String generatedCode = code.toString();
        LOGGER.info("Generated gift code: " + generatedCode);
        return generatedCode;
    }
    
    /**
     * Generate a referral code (6 characters)
     */
    public static String generateReferralCode() {
        StringBuilder code = new StringBuilder(6);
        
        for (int i = 0; i < 6; i++) {
            int index = RANDOM.nextInt(CHARACTERS.length());
            code.append(CHARACTERS.charAt(index));
        }
        
        return code.toString();
    }
    
    /**
     * Validate gift code format
     */
    public static boolean isValidGiftCodeFormat(String code) {
        if (code == null || code.length() != 8) {
            return false;
        }
        
        return code.matches("^[A-Z0-9]{8}$");
    }
}