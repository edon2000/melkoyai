package elonmusk.service;

import elonmusk.model.GiftCode;
import elonmusk.model.GiftCodeRedemption;
import elonmusk.model.User;
import elonmusk.exception.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.agroal.api.AgroalDataSource;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@ApplicationScoped
public class GiftCodeService {
    
    private static final Logger LOGGER = Logger.getLogger(GiftCodeService.class.getName());
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9]{8}$");
    
    @Inject
    AgroalDataSource dataSource;
    
    @Inject
    UserService userService;
    
    public String validateGiftCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return "Gift code is required";
        }
        
        code = code.trim();
        if (code.length() != 8) {
            return "Gift code must be exactly 8 characters";
        }
        
        if (!CODE_PATTERN.matcher(code).matches()) {
            return "Invalid code format. Must contain only uppercase letters (A-Z) and numbers (0-9)";
        }
        
        return null; // Valid
    }
    
    public GiftCode findActiveCode(String code) {
        try {
            // Clean up expired codes first
            cleanupExpiredCodes();
            
            String sql = "SELECT * FROM gift_codes WHERE code = ? AND is_active = true AND expires_at > CURRENT_TIMESTAMP";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, code);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return mapGiftCodeResultSet(rs);
                    }
                }
            }
            return null;
        } catch (SQLException e) {
            LOGGER.severe("Database error finding active gift code: " + e.getMessage());
            return null;
        }
    }
    
    public boolean hasUserRedeemedCode(Long userId, Long giftCodeId) {
        try {
            String sql = "SELECT COUNT(*) FROM gift_code_redemptions WHERE user_id = ? AND gift_code_id = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, userId);
                stmt.setLong(2, giftCodeId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1) > 0;
                    }
                }
            }
            return false;
        } catch (SQLException e) {
            LOGGER.severe("Database error checking user redemption: " + e.getMessage());
            return false;
        }
    }
    
    public boolean redeemGiftCode(Long userId, String code) {
        try {
            String validationError = validateGiftCode(code);
            if (validationError != null) {
                LOGGER.warning("Gift code validation failed for user " + userId + ", code: " + code + ", error: " + validationError);
                throw new ValidationException(validationError);
            }
            
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                
                try {
                    // Find and validate gift code
                    GiftCode giftCode = findActiveCode(code);
                    if (giftCode == null) {
                        LOGGER.warning("Gift code not found or inactive: " + code);
                        throw new ValidationException("Invalid or inactive gift code");
                    }
                    
                    LOGGER.info("Found gift code: " + code + ", expires at: " + giftCode.expiresAt + ", current time: " + LocalDateTime.now());
                    
                    if (giftCode.isExpired()) {
                        LOGGER.warning("Gift code expired: " + code + ", expired at: " + giftCode.expiresAt);
                        throw new ValidationException("This gift code has expired");
                    }
                    
                    if (!giftCode.canBeUsed()) {
                        LOGGER.warning("Gift code cannot be used: " + code + ", uses: " + giftCode.currentUses + "/" + giftCode.maxUses);
                        throw new ValidationException("This gift code has reached maximum usage limit");
                    }
                    
                    // Check if user already redeemed this code
                    if (hasUserRedeemedCode(userId, giftCode.id)) {
                        LOGGER.warning("User " + userId + " already redeemed code: " + code);
                        throw new ValidationException("You have already redeemed this gift code");
                    }
                    
                    // Update gift code usage count
                    String updateCodeSql = "UPDATE gift_codes SET current_uses = current_uses + 1 WHERE id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(updateCodeSql)) {
                        stmt.setLong(1, giftCode.id);
                        int updated = stmt.executeUpdate();
                        if (updated == 0) {
                            throw new ValidationException("Failed to update gift code usage");
                        }
                    }
                    
                    // Record redemption
                    String insertRedemptionSql = "INSERT INTO gift_code_redemptions (user_id, gift_code_id, code, amount) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement stmt = conn.prepareStatement(insertRedemptionSql)) {
                        stmt.setLong(1, userId);
                        stmt.setLong(2, giftCode.id);
                        stmt.setString(3, code);
                        stmt.setBigDecimal(4, giftCode.amount);
                        int inserted = stmt.executeUpdate();
                        if (inserted == 0) {
                            throw new ValidationException("Failed to record gift code redemption");
                        }
                    }
                    
                    // Update user wallet balance
                    User user = userService.findById(userId);
                    if (user == null) {
                        throw new ValidationException("User not found");
                    }
                    BigDecimal newBalance = user.walletBalance.add(giftCode.amount);
                    userService.updateWalletBalance(userId, newBalance);
                    
                    conn.commit();
                    LOGGER.info("Gift code redeemed successfully: User " + userId + " redeemed " + code + " for $" + giftCode.amount);
                    return true;
                    
                } catch (Exception e) {
                    conn.rollback();
                    LOGGER.severe("Error redeeming gift code: " + e.getMessage());
                    throw e;
                }
            }
        } catch (SQLException e) {
            LOGGER.severe("Database error redeeming gift code: " + e.getMessage());
            throw new ValidationException("Database error occurred while redeeming gift code");
        } catch (Exception e) {
            LOGGER.severe("Unexpected error redeeming gift code: " + e.getMessage());
            if (e instanceof ValidationException) {
                throw e;
            }
            throw new ValidationException("Failed to redeem gift code: " + e.getMessage());
        }
    }
    
    public List<GiftCodeRedemption> getUserRedemptionHistory(Long userId, int limit) {
        try {
            String sql = "SELECT * FROM gift_code_redemptions WHERE user_id = ? ORDER BY redeemed_at DESC LIMIT ?";
            List<GiftCodeRedemption> redemptions = new ArrayList<>();
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, userId);
                stmt.setInt(2, limit);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        redemptions.add(mapRedemptionResultSet(rs));
                    }
                }
            }
            return redemptions;
        } catch (SQLException e) {
            LOGGER.severe("Database error getting user redemption history: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    public BigDecimal getTotalGiftEarnings(Long userId) {
        try {
            String sql = "SELECT COALESCE(SUM(amount), 0) as total FROM gift_code_redemptions WHERE user_id = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getBigDecimal("total");
                    }
                }
            }
            return BigDecimal.ZERO;
        } catch (SQLException e) {
            LOGGER.severe("Database error getting total gift earnings: " + e.getMessage());
            return BigDecimal.ZERO;
        }
    }
    
    // Auto-cleanup expired gift codes
    public void cleanupExpiredCodes() {
        try {
            String sql = "DELETE FROM gift_codes WHERE expires_at < CURRENT_TIMESTAMP";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                int deleted = stmt.executeUpdate();
                if (deleted > 0) {
                    LOGGER.info("Cleaned up " + deleted + " expired gift codes");
                }
            }
        } catch (SQLException e) {
            LOGGER.severe("Error cleaning up expired codes: " + e.getMessage());
        }
    }
    
    /**
     * Create a new gift code with comprehensive validation
     * @param code The gift code (must be unique, 8 characters, A-Z0-9)
     * @param amount The amount for the gift code (must be > 0)
     * @param durationMinutes Duration in minutes (1 to 43200, max 30 days)
     * @throws ValidationException if validation fails
     */
    public void createGiftCode(String code, BigDecimal amount, int durationMinutes) {
        LOGGER.info("Creating gift code: " + code + ", amount: $" + amount + ", duration: " + durationMinutes + " minutes");
        try {
            // Test database connection first
            try (Connection testConn = dataSource.getConnection()) {
                LOGGER.info("Database connection successful");
            } catch (SQLException connEx) {
                LOGGER.severe("Database connection failed: " + connEx.getMessage());
                throw new ValidationException("Database connection failed: " + connEx.getMessage());
            }
            // Clean up expired codes first
            cleanupExpiredCodes();
            
            // Validate code format
            String validationError = validateGiftCode(code);
            if (validationError != null) {
                throw new ValidationException(validationError);
            }
            
            // Normalize code to uppercase
            code = code.trim().toUpperCase();
            
            // Validate amount
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException("Gift code amount must be greater than 0");
            }
            
            // Validate amount range (prevent unreasonably large amounts)
            BigDecimal maxAmount = new BigDecimal("100000");
            if (amount.compareTo(maxAmount) > 0) {
                throw new ValidationException("Gift code amount cannot exceed $" + maxAmount);
            }
            
            // Validate duration
            if (durationMinutes <= 0 || durationMinutes > 43200) {
                throw new ValidationException("Duration must be between 1 and 43200 minutes (30 days)");
            }
            
            // Check if code already exists (including inactive codes to prevent reuse)
            String checkSql = "SELECT COUNT(*) FROM gift_codes WHERE UPPER(code) = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, code);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        throw new ValidationException("Gift code already exists: " + code);
                    }
                }
            }
            
            LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(durationMinutes);
            
            // Insert gift code with all validations passed
            String sql = "INSERT INTO gift_codes (code, amount, expires_at, max_uses, current_uses, is_active, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, code);
                stmt.setBigDecimal(2, amount);
                stmt.setTimestamp(3, Timestamp.valueOf(expiresAt));
                stmt.setInt(4, 1000); // max_uses
                stmt.setInt(5, 0); // current_uses
                stmt.setBoolean(6, true); // is_active
                stmt.setString(7, "ADMIN"); // created_by
                stmt.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now())); // created_at
                
                int inserted = stmt.executeUpdate();
                if (inserted == 0) {
                    throw new ValidationException("Failed to create gift code");
                }
                
                LOGGER.info("Gift code created successfully: " + code + " ($" + amount + ") expires at " + expiresAt);
            }
        } catch (SQLException e) {
            LOGGER.severe("Database error creating gift code: " + e.getMessage());
            LOGGER.severe("SQLState: " + e.getSQLState());
            LOGGER.severe("Error Code: " + e.getErrorCode());
            e.printStackTrace();
            // Handle unique constraint violation (PostgreSQL SQLState 23505)
            if ("23505".equals(e.getSQLState()) && e.getMessage().contains("gift_codes_code_key")) {
                throw new ValidationException("Gift code already exists: " + code);
            }
            throw new ValidationException("Database error: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.severe("Unexpected error creating gift code: " + e.getMessage());
            if (e instanceof ValidationException) {
                throw e;
            }
            throw new ValidationException("Failed to create gift code: " + e.getMessage());
        }
    }
    
    private GiftCode mapGiftCodeResultSet(ResultSet rs) throws SQLException {
        GiftCode code = new GiftCode();
        code.id = rs.getLong("id");
        code.code = rs.getString("code");
        code.amount = rs.getBigDecimal("amount");
        code.expiresAt = rs.getTimestamp("expires_at").toLocalDateTime();
        code.maxUses = rs.getInt("max_uses");
        code.currentUses = rs.getInt("current_uses");
        code.isActive = rs.getBoolean("is_active");
        code.createdBy = rs.getString("created_by");
        code.createdAt = rs.getTimestamp("created_at").toLocalDateTime();
        return code;
    }
    
    private GiftCodeRedemption mapRedemptionResultSet(ResultSet rs) throws SQLException {
        GiftCodeRedemption redemption = new GiftCodeRedemption();
        redemption.id = rs.getLong("id");
        redemption.userId = rs.getLong("user_id");
        redemption.giftCodeId = rs.getLong("gift_code_id");
        redemption.code = rs.getString("code");
        redemption.amount = rs.getBigDecimal("amount");
        redemption.redeemedAt = rs.getTimestamp("redeemed_at").toLocalDateTime();
        return redemption;
    }
}