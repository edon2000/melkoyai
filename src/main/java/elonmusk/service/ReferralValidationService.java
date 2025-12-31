package elonmusk.service;

import elonmusk.model.User;
import elonmusk.exception.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class ReferralValidationService {
    
    private static final Logger LOGGER = Logger.getLogger(ReferralValidationService.class.getName());
    private static final int MAX_REFERRALS_PER_USER = 1000;
    private static final int REFERRAL_EXPIRY_HOURS = 72;
    private static final int MAX_DAILY_REFERRALS = 10;
    private static final long LOCK_TIMEOUT_SECONDS = 5;
    
    // Thread-safe locks with cleanup mechanism
    private final ConcurrentHashMap<Long, ReentrantLock> userLocks = new ConcurrentHashMap<>();
    
    @Inject
    DataSource dataSource;
    
    @Inject
    UserService userService;
    
    /**
     * Comprehensive referral validation with proper error handling
     */
    public ReferralValidationResult validateReferralAcceptance(Long userId, String referralCode) {
        ReentrantLock userLock = userLocks.computeIfAbsent(userId, k -> new ReentrantLock());
        
        try {
            if (!userLock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                LOGGER.warning("Failed to acquire lock for user validation: " + sanitizeForLog(userId));
                return createErrorResult("SYSTEM_BUSY", "System is busy, please try again");
            }
            
            try {
                return performValidation(userId, referralCode);
            } finally {
                userLock.unlock();
                // Cleanup unused locks periodically
                if (userLocks.size() > 1000) {
                    cleanupUnusedLocks();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warning("Validation interrupted for user: " + sanitizeForLog(userId));
            return createErrorResult("SYSTEM_ERROR", "Validation interrupted");
        }
    }
    
    private ReferralValidationResult performValidation(Long userId, String referralCode) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // 1. Validate user exists
                if (!validateUserExists(conn, userId)) {
                    conn.rollback();
                    return createErrorResult("USER_NOT_FOUND", "User not found");
                }
                
                // 2. Validate referrer exists
                User referrer = findReferrerByCode(conn, referralCode);
                if (referrer == null) {
                    conn.rollback();
                    return createErrorResult("INVALID_REFERRAL_CODE", "Invalid referral code");
                }
                
                // 3. Prevent self-referral
                if (referrer.id.equals(userId)) {
                    conn.rollback();
                    return createErrorResult("SELF_REFERRAL_NOT_ALLOWED", "Cannot refer yourself");
                }
                
                // 4. Check existing referrals
                if (hasExistingReferral(conn, userId)) {
                    conn.rollback();
                    return createErrorResult("ALREADY_HAS_REFERRAL", "User already has a referral");
                }
                
                // 5. Check referrer limits
                if (hasReachedMaxReferrals(conn, referrer.id)) {
                    conn.rollback();
                    return createErrorResult("REFERRER_MAX_LIMIT_REACHED", "Referrer limit reached");
                }
                
                // 6. Check for suspicious activity
                if (hasSuspiciousActivity(conn, referrer.id)) {
                    conn.rollback();
                    return createErrorResult("SUSPICIOUS_ACTIVITY", "Too many referrals detected");
                }
                
                conn.commit();
                
                ReferralValidationResult result = new ReferralValidationResult();
                result.isValid = true;
                result.referrerId = referrer.id;
                result.referrerName = referrer.name;
                result.message = "Validation successful";
                
                LOGGER.info("Referral validation PASSED for user: " + sanitizeForLog(userId));
                return result;
                
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
            
        } catch (SQLException e) {
            LOGGER.severe("Database error during validation: " + e.getMessage());
            return createErrorResult("DATABASE_ERROR", "System error during validation");
        } catch (Exception e) {
            LOGGER.severe("Unexpected error during validation: " + e.getMessage());
            return createErrorResult("SYSTEM_ERROR", "Unexpected system error");
        }
    }
    
    private boolean validateUserExists(Connection conn, Long userId) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE id = ? LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }
    
    private User findReferrerByCode(Connection conn, String referralCode) throws SQLException {
        String sql = "SELECT id, name FROM users WHERE referral_code = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, referralCode);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    User referrer = new User();
                    referrer.id = rs.getLong("id");
                    referrer.name = rs.getString("name");
                    return referrer;
                }
            }
        }
        return null;
    }
    
    private boolean hasExistingReferral(Connection conn, Long userId) throws SQLException {
        String sql = "SELECT 1 FROM referral_acceptances WHERE referred_user_id = ? AND status IN ('PENDING', 'ACCEPTED') LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }
    
    private boolean hasReachedMaxReferrals(Connection conn, Long referrerId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM referral_acceptances WHERE referrer_id = ? AND status = 'ACCEPTED'";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, referrerId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) >= MAX_REFERRALS_PER_USER;
            }
        }
    }
    
    private boolean hasSuspiciousActivity(Connection conn, Long referrerId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM referral_acceptances " +
                    "WHERE referrer_id = ? AND created_at > CURRENT_TIMESTAMP - INTERVAL '1 day'";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, referrerId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > MAX_DAILY_REFERRALS;
            }
        }
    }
    
    /**
     * Atomic referral acceptance with proper error handling
     */
    public boolean acceptReferralWithValidation(Long userId) {
        ReentrantLock userLock = userLocks.computeIfAbsent(userId, k -> new ReentrantLock());
        
        try {
            if (!userLock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                LOGGER.warning("Failed to acquire lock for referral acceptance: " + sanitizeForLog(userId));
                return false;
            }
            
            try {
                return performReferralAcceptance(userId);
            } finally {
                userLock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warning("Referral acceptance interrupted for user: " + sanitizeForLog(userId));
            return false;
        }
    }
    
    private boolean performReferralAcceptance(Long userId) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Get pending referral
                String getPendingSql = "SELECT referrer_id, created_at FROM referral_acceptances " +
                                      "WHERE referred_user_id = ? AND status = 'PENDING' FOR UPDATE";
                
                Long referrerId = null;
                LocalDateTime createdAt = null;
                
                try (PreparedStatement stmt = conn.prepareStatement(getPendingSql)) {
                    stmt.setLong(1, userId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            referrerId = rs.getLong("referrer_id");
                            java.sql.Timestamp timestamp = rs.getTimestamp("created_at");
                            if (timestamp != null) {
                                createdAt = timestamp.toLocalDateTime();
                            }
                        } else {
                            conn.rollback();
                            return false;
                        }
                    }
                }
                
                // Check expiration
                if (createdAt != null && createdAt.plusHours(REFERRAL_EXPIRY_HOURS).isBefore(LocalDateTime.now())) {
                    String expireSql = "UPDATE referral_acceptances SET status = 'EXPIRED' WHERE referred_user_id = ? AND status = 'PENDING'";
                    try (PreparedStatement stmt = conn.prepareStatement(expireSql)) {
                        stmt.setLong(1, userId);
                        stmt.executeUpdate();
                    }
                    conn.commit();
                    return false;
                }
                
                // Update to ACCEPTED
                String updateSql = "UPDATE referral_acceptances SET status = 'ACCEPTED', processed_at = ? WHERE referred_user_id = ? AND status = 'PENDING'";
                try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                    stmt.setTimestamp(1, java.sql.Timestamp.valueOf(LocalDateTime.now()));
                    stmt.setLong(2, userId);
                    if (stmt.executeUpdate() == 0) {
                        conn.rollback();
                        return false;
                    }
                }
                
                // Update user
                String updateUserSql = "UPDATE users SET referred_by = ?, updated_at = ? WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(updateUserSql)) {
                    stmt.setLong(1, referrerId);
                    stmt.setTimestamp(2, java.sql.Timestamp.valueOf(LocalDateTime.now()));
                    stmt.setLong(3, userId);
                    stmt.executeUpdate();
                }
                
                // Increment referrer count
                String updateReferrerSql = "UPDATE users SET total_referrals = COALESCE(total_referrals, 0) + 1, updated_at = ? WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(updateReferrerSql)) {
                    stmt.setTimestamp(1, java.sql.Timestamp.valueOf(LocalDateTime.now()));
                    stmt.setLong(2, referrerId);
                    if (stmt.executeUpdate() == 0) {
                        conn.rollback();
                        return false;
                    }
                }
                
                // Create invitation record
                String insertInvitationSql = "INSERT INTO referral_invitations (referrer_id, referred_user_id, referral_code, status, created_at) " +
                                            "SELECT ?, ?, referral_code, 'ACTIVE', ? FROM users WHERE id = ? " +
                                            "ON CONFLICT (referrer_id, referred_user_id) DO NOTHING";
                try (PreparedStatement stmt = conn.prepareStatement(insertInvitationSql)) {
                    stmt.setLong(1, referrerId);
                    stmt.setLong(2, userId);
                    stmt.setTimestamp(3, java.sql.Timestamp.valueOf(LocalDateTime.now()));
                    stmt.setLong(4, referrerId);
                    stmt.executeUpdate();
                }
                
                conn.commit();
                LOGGER.info("Referral accepted successfully: " + sanitizeForLog(userId));
                return true;
                
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
            
        } catch (SQLException e) {
            LOGGER.severe("Database error during referral acceptance: " + e.getMessage());
            return false;
        } catch (Exception e) {
            LOGGER.severe("Unexpected error during referral acceptance: " + e.getMessage());
            return false;
        }
    }
    
    private ReferralValidationResult createErrorResult(String errorCode, String message) {
        ReferralValidationResult result = new ReferralValidationResult();
        result.isValid = false;
        result.errorCode = errorCode;
        result.message = message;
        return result;
    }
    
    private String sanitizeForLog(Object input) {
        if (input == null) return "null";
        return input.toString().replaceAll("[\\r\\n\\t]", "_");
    }
    
    private void cleanupUnusedLocks() {
        // Remove locks that are not currently held
        userLocks.entrySet().removeIf(entry -> !entry.getValue().isLocked());
    }
    
    public static class ReferralValidationResult {
        public boolean isValid = false;
        public String errorCode;
        public String message;
        public Long referrerId;
        public String referrerName;
    }
}