package elonmusk.service;

import elonmusk.dto.LoginFormRequest;
import elonmusk.dto.RegisterFormRequest;
import elonmusk.model.User;
import elonmusk.exception.ValidationException;
import elonmusk.exception.ServiceException;
import elonmusk.filter.SecurityUtil;
import elonmusk.validation.InputValidator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.logging.Logger;
import java.util.logging.Level;

@ApplicationScoped
public class AuthService {

    private static final Logger LOGGER = Logger.getLogger(AuthService.class.getName());

    @Inject
    User userService;

    @Inject
    elonmusk.service.UserService userServiceBean;

    @Inject
    ReferralBonusService referralBonusService;

    public User authenticate(LoginFormRequest request) {
        if (request == null) {
            throw new ValidationException("Login request is required");
        }

        try {
            // Validate and sanitize inputs
            InputValidator.validatePhoneNumber(request.account);
            InputValidator.validatePassword(request.password);

            String sanitizedAccount = SecurityUtil.sanitizeInput(request.account);

            User user = userService.findByPhoneNumber(sanitizedAccount);
            if (user == null) {
                LOGGER.warning("Authentication failed: Phone number not found - " + sanitizedAccount);
                throw new ValidationException("Invalid phone number or password");
            }

            // Verify password using BCrypt
            boolean passwordValid = SecurityUtil.verifyPassword(request.password, user.password);

            if (!passwordValid) {
                LOGGER.warning("Authentication failed: Invalid password for user - " + sanitizedAccount);
                throw new ValidationException("Invalid phone number or password");
            }

            LOGGER.info("User authenticated successfully: " + sanitizedAccount);
            return user;
        } catch (ValidationException e) {
            throw e;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error during authentication", e);
            throw new ServiceException("Authentication service temporarily unavailable", e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error during authentication", e);
            throw new ServiceException("Authentication failed", e);
        }
    }

    public User register(RegisterFormRequest request) {
        if (request == null) {
            throw new ValidationException("Registration request is required");
        }

        try {
            LOGGER.info("Registration attempt for: " + SecurityUtil.sanitizeInput(request.account));

            // Comprehensive input validation
            InputValidator.validateName(request.name);
            InputValidator.validatePhoneNumber(request.account);
            InputValidator.validateEmail(request.email);
            InputValidator.validatePassword(request.password);

            // Sanitize inputs
            String sanitizedName = SecurityUtil.sanitizeInput(request.name);
            String sanitizedAccount = SecurityUtil.sanitizeInput(request.account);
            String sanitizedEmail = SecurityUtil.sanitizeInput(request.email);

            // Check for existing phone number
            User existingPhone = userService.findByPhoneNumber(sanitizedAccount);
            if (existingPhone != null) {
                LOGGER.warning("Registration failed: Phone number already exists - " + sanitizedAccount);
                throw new ValidationException("Phone number already registered");
            }

            // Check for existing email
            User existingEmail = userService.findByEmail(sanitizedEmail);
            if (existingEmail != null) {
                LOGGER.warning("Registration failed: Email already exists - " + sanitizedEmail);
                throw new ValidationException("Email already registered");
            }

            // Hash password before storing
            String hashedPassword = SecurityUtil.hashPassword(request.password);

            // Create new user
            User newUser = userServiceBean.createUser(
                    sanitizedName,
                    sanitizedAccount,
                    sanitizedEmail,
                    hashedPassword,
                    generateReferralCode(sanitizedAccount)
            );

            LOGGER.info("User registered successfully with ID: " + newUser.id);
            return newUser;
        } catch (ValidationException e) {
            throw e;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error during registration", e);
            throw new ServiceException("Registration service temporarily unavailable", e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error during registration", e);
            throw new ServiceException("Registration failed", e);
        }
    }

    public void checkDailyBonus(User user) {
        // Daily bonus logic can be implemented here
    }

    public void generateResetToken(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            throw new ValidationException("Phone number is required");
        }

        try {
            InputValidator.validatePhoneNumber(phone);
            String sanitizedPhone = SecurityUtil.sanitizeInput(phone);

            User user = userService.findByPhoneNumber(sanitizedPhone);
            if (user == null) {
                LOGGER.warning("Password reset requested for non-existent phone: " + sanitizedPhone);
                // Don't reveal if phone exists or not for security
                return;
            }

            // TODO: Implement secure reset token generation and storage
            LOGGER.info("Password reset token generated for user: " + user.id);
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error generating reset token", e);
            throw new ServiceException("Password reset service temporarily unavailable", e);
        }
    }

    private String generateReferralCode(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            throw new ValidationException("Invalid phone number for referral code generation");
        }
        return SecurityUtil.generateReferralCode(phoneNumber);
    }

    public void processReferral(String referralCode, User newUser) {
        if (referralCode == null || referralCode.trim().isEmpty()) {
            return; // No referral code provided
        }

        if (newUser == null || newUser.id == null) {
            throw new ValidationException("Valid user is required for referral processing");
        }

        try {
            // Use the enhanced referral validation service
            boolean validated = referralBonusService.validateReferralInvitation(referralCode, newUser);

            if (validated) {
                LOGGER.info("Referral invitation validated successfully for user: " + newUser.id);
            } else {
                LOGGER.warning("Referral invitation validation failed for code: " + referralCode);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing referral", e);
            throw new ServiceException("Referral processing failed", e);
        }
    }

    public void processReferralReward(User referrer, BigDecimal investmentAmount) {
        if (referrer == null || referrer.id == null) {
            throw new ValidationException("Valid referrer is required");
        }

        if (investmentAmount == null || investmentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Valid investment amount is required");
        }

        try {
            // Calculate 10% referral reward with proper validation
            BigDecimal rewardRate = new BigDecimal("0.10");
            BigDecimal reward = investmentAmount.multiply(rewardRate);

            // Validate reward amount
            if (reward.compareTo(BigDecimal.ZERO) <= 0) {
                LOGGER.warning("Invalid reward amount calculated: " + reward);
                return;
            }

            try (java.sql.Connection conn = userService.dataSource.getConnection();
                 java.sql.PreparedStatement stmt = conn.prepareStatement(
                         "UPDATE users SET wallet_balance = wallet_balance + ?, referral_earnings = COALESCE(referral_earnings, 0) + ?, updated_at = ? WHERE id = ?")) {
                stmt.setBigDecimal(1, reward);
                stmt.setBigDecimal(2, reward);
                stmt.setTimestamp(3, java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()));
                stmt.setLong(4, referrer.id);

                int updated = stmt.executeUpdate();
                if (updated > 0) {
                    LOGGER.info("Referral reward processed: " + reward + " ETB for user " + referrer.id);
                } else {
                    LOGGER.warning("No rows updated for referral reward - user may not exist: " + referrer.id);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error processing referral reward", e);
            throw new ServiceException("Failed to process referral reward", e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error processing referral reward", e);
            throw new ServiceException("Referral reward processing failed", e);
        }
    }
}