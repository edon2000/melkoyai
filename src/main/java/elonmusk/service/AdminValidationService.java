package elonmusk.service;

import elonmusk.model.User;
import elonmusk.exception.ValidationException;
import elonmusk.exception.ServiceException;
import elonmusk.validation.InputValidator;
import elonmusk.filter.SecurityUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.logging.Logger;
import java.math.BigDecimal;
import java.util.regex.Pattern;

@ApplicationScoped
public class AdminValidationService {
    
    private static final Logger LOGGER = Logger.getLogger(AdminValidationService.class.getName());
    
    @Inject
    UserService userService;
    
    private static final Pattern MALICIOUS_PATTERN = Pattern.compile(
        "(?i).*(script|javascript|vbscript|onload|onerror|onclick|alert|confirm|prompt|eval|expression|import|meta|link|iframe|object|embed|form|input|select|textarea|button).*"
    );
    
    public void validateUserUpdate(Long userId, String name, String email, String balanceStr) {
        if (userId == null || userId <= 0) {
            throw new ValidationException("Invalid user ID provided");
        }
        
        User existingUser = userService.findById(userId);
        if (existingUser == null) {
            throw new ValidationException("User not found with ID: " + userId);
        }
        
        if (name != null && !name.trim().isEmpty()) {
            validateAdminInput(name, "Name");
            InputValidator.validateName(name);
            
            if (name.trim().length() > 100) {
                throw new ValidationException("Name cannot exceed 100 characters");
            }
        }
        
        if (email != null && !email.trim().isEmpty()) {
            validateAdminInput(email, "Email");
            InputValidator.validateEmail(email);
            
            User emailUser = userService.findByEmail(email.trim());
            if (emailUser != null && !emailUser.id.equals(userId)) {
                throw new ValidationException("Email already exists for another user");
            }
        }
        
        if (balanceStr != null && !balanceStr.trim().isEmpty()) {
            validateAdminInput(balanceStr, "Balance");
            
            try {
                BigDecimal balance = new BigDecimal(balanceStr.trim());
                if (balance.compareTo(BigDecimal.ZERO) < 0) {
                    throw new ValidationException("Balance cannot be negative");
                }
                if (balance.compareTo(new BigDecimal("10000000")) > 0) {
                    throw new ValidationException("Balance cannot exceed $10,000,000");
                }
            } catch (NumberFormatException e) {
                throw new ValidationException("Invalid balance format");
            }
        }
        
        LOGGER.info("Admin user update validation passed for user ID: " + userId);
    }
    
    public void validateUserDeletion(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ValidationException("Invalid user ID provided");
        }
        
        User existingUser = userService.findById(userId);
        if (existingUser == null) {
            throw new ValidationException("User not found with ID: " + userId);
        }
        
        if (existingUser.walletBalance != null && existingUser.walletBalance.compareTo(BigDecimal.ZERO) > 0) {
            LOGGER.warning("Attempting to delete user with non-zero balance: " + userId + " (Balance: " + existingUser.walletBalance + ")");
        }
        
        LOGGER.info("Admin user deletion validation passed for user ID: " + userId);
    }
    
    public void validateAdminCredentials(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            throw new ValidationException("Username is required");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new ValidationException("Password is required");
        }
        
        username = SecurityUtil.sanitizeInput(username.trim());
        password = SecurityUtil.sanitizeInput(password.trim());
        
        validateAdminInput(username, "Username");
        validateAdminInput(password, "Password");
        
        if (username.length() > 50) {
            throw new ValidationException("Username too long");
        }
        if (password.length() > 100) {
            throw new ValidationException("Password too long");
        }
        
        LOGGER.info("Admin credentials validation passed for username: " + username);
    }
    
    public void validateAdminSession(String adminId) {
        if (adminId == null || adminId.trim().isEmpty()) {
            throw new ValidationException("Admin session not found");
        }
        
        if (!"admin_authenticated".equals(adminId)) {
            throw new ValidationException("Invalid admin session");
        }
    }
    
    private void validateAdminInput(String input, String fieldName) {
        if (input == null) {
            return;
        }
        
        if (MALICIOUS_PATTERN.matcher(input).matches()) {
            LOGGER.warning("Malicious input detected in " + fieldName + ": " + input);
            throw new ValidationException("Invalid characters detected in " + fieldName);
        }
        
        String lowerInput = input.toLowerCase();
        if (lowerInput.contains("drop ") || lowerInput.contains("delete ") || 
            lowerInput.contains("insert ") || lowerInput.contains("update ") ||
            lowerInput.contains("select ") || lowerInput.contains("union ") ||
            lowerInput.contains("--") || lowerInput.contains("/*") || lowerInput.contains("*/")) {
            LOGGER.warning("SQL injection attempt detected in " + fieldName + ": " + input);
            throw new ValidationException("Invalid content detected in " + fieldName);
        }
        
        if (lowerInput.contains("<script") || lowerInput.contains("javascript:") ||
            lowerInput.contains("onload=") || lowerInput.contains("onerror=") ||
            lowerInput.contains("onclick=") || lowerInput.contains("alert(")) {
            LOGGER.warning("XSS attempt detected in " + fieldName + ": " + input);
            throw new ValidationException("Invalid content detected in " + fieldName);
        }
    }
    
    public void logAdminOperation(String operation, String details) {
        LOGGER.info("ADMIN_AUDIT: " + operation + " - " + details);
    }
    
    public void validateAdminPermission(String operation) {
        if (operation == null || operation.trim().isEmpty()) {
            throw new ValidationException("Operation not specified");
        }
        
        String[] allowedOperations = {
            "VIEW_USERS", "UPDATE_USER", "DELETE_USER", 
            "VIEW_DEPOSITS", "APPROVE_DEPOSIT", "REJECT_DEPOSIT",
            "VIEW_WITHDRAWALS", "APPROVE_WITHDRAWAL", "REJECT_WITHDRAWAL"
        };
        
        boolean isAllowed = false;
        for (String allowed : allowedOperations) {
            if (allowed.equals(operation)) {
                isAllowed = true;
                break;
            }
        }
        
        if (!isAllowed) {
            LOGGER.warning("Unauthorized admin operation attempted: " + operation);
            throw new ValidationException("Operation not permitted");
        }
    }
}