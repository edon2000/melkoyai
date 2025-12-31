package elonmusk.service;

import elonmusk.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.logging.Logger;

@ApplicationScoped
public class ReferralAcceptanceService {
    
    private static final Logger LOGGER = Logger.getLogger(ReferralAcceptanceService.class.getName());
    
    @Inject
    DataSource dataSource;
    
    @Inject
    UserService userService;
    
    public void createPendingReferral(Long referredUserId, String referralCode) {
        User referrer = userService.findByReferralCode(referralCode);
        if (referrer == null) return;
        
        // Check if user already has a pending or accepted referral
        if (hasPendingOrAcceptedReferral(referredUserId)) {
            LOGGER.warning("User " + referredUserId + " already has a referral - cannot create another");
            return;
        }
        
        String sql = "INSERT INTO referral_acceptances (referred_user_id, referrer_id, referral_code, status, created_at) VALUES (?, ?, ?, 'PENDING', ?) ON CONFLICT (referred_user_id, referrer_id) DO NOTHING";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, referredUserId);
            stmt.setLong(2, referrer.id);
            stmt.setString(3, referralCode);
            stmt.setTimestamp(4, java.sql.Timestamp.valueOf(LocalDateTime.now()));
            
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                LOGGER.info("Created pending referral for user " + referredUserId + " from referrer " + referrer.id);
            } else {
                LOGGER.info("Referral already exists for user " + referredUserId + " from referrer " + referrer.id);
            }
            
        } catch (SQLException e) {
            LOGGER.warning("Failed to create pending referral: " + e.getMessage());
        }
    }
    
    /**
     * Check if user already has a pending or accepted referral
     */
    private boolean hasPendingOrAcceptedReferral(Long userId) {
        String sql = "SELECT COUNT(*) FROM referral_acceptances WHERE referred_user_id = ? AND status IN ('PENDING', 'ACCEPTED')";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
            return false;
        } catch (SQLException e) {
            LOGGER.warning("Error checking existing referral: " + e.getMessage());
            return true; // Fail safe - assume user already has referral
        }
    }
    
    public void acceptReferralAutomatically(Long referredUserId, Long referrerId, String referralCode) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            
            // Check if user already has an accepted referral
            String checkAcceptedSql = "SELECT COUNT(*) FROM referral_acceptances WHERE referred_user_id = ? AND status = 'ACCEPTED'";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkAcceptedSql)) {
                checkStmt.setLong(1, referredUserId);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        LOGGER.warning("User " + referredUserId + " already has an accepted referral - cannot accept another");
                        conn.rollback();
                        return;
                    }
                }
            }
            
            // Create ACCEPTED referral record directly (no pending state)
            String insertSql = "INSERT INTO referral_acceptances (referred_user_id, referrer_id, referral_code, status, created_at, processed_at) VALUES (?, ?, ?, 'ACCEPTED', ?, ?) ON CONFLICT (referred_user_id, referrer_id) DO NOTHING";
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                LocalDateTime now = LocalDateTime.now();
                stmt.setLong(1, referredUserId);
                stmt.setLong(2, referrerId);
                stmt.setString(3, referralCode);
                stmt.setTimestamp(4, java.sql.Timestamp.valueOf(now));
                stmt.setTimestamp(5, java.sql.Timestamp.valueOf(now));
                
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected == 0) {
                    LOGGER.info("Referral already exists for user " + referredUserId + " from referrer " + referrerId);
                    conn.rollback();
                    return;
                }
            }
            
            // Update user's referred_by field
            String updateUserSql = "UPDATE users SET referred_by = ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updateUserSql)) {
                stmt.setLong(1, referrerId);
                stmt.setLong(2, referredUserId);
                stmt.executeUpdate();
            }
            
            // Increment referrer's total_referrals count
            String updateReferrerSql = "UPDATE users SET total_referrals = COALESCE(total_referrals, 0) + 1, updated_at = ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updateReferrerSql)) {
                stmt.setTimestamp(1, java.sql.Timestamp.valueOf(LocalDateTime.now()));
                stmt.setLong(2, referrerId);
                stmt.executeUpdate();
            }
            
            // Create referral invitation record
            String insertInvitationSql = "INSERT INTO referral_invitations (referrer_id, referred_user_id, referral_code, status, created_at) VALUES (?, ?, ?, 'ACTIVE', ?) ON CONFLICT (referrer_id, referred_user_id) DO NOTHING";
            try (PreparedStatement stmt = conn.prepareStatement(insertInvitationSql)) {
                stmt.setLong(1, referrerId);
                stmt.setLong(2, referredUserId);
                stmt.setString(3, referralCode);
                stmt.setTimestamp(4, java.sql.Timestamp.valueOf(LocalDateTime.now()));
                stmt.executeUpdate();
            }
            
            conn.commit();
            LOGGER.info("Referral AUTOMATICALLY ACCEPTED: User " + referredUserId + " from referrer " + referrerId);
            
        } catch (SQLException e) {
            LOGGER.warning("Failed to automatically accept referral: " + e.getMessage());
        }
    }
    
    public void acceptReferral(Long userId) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            
            // FIXED: Use row-level locking to prevent race conditions
            String lockAndCheckSql = "SELECT referrer_id, created_at FROM referral_acceptances " +
                                   "WHERE referred_user_id = ? AND status = 'PENDING' FOR UPDATE";
            Long referrerId = null;
            LocalDateTime createdAt = null;
            
            try (PreparedStatement stmt = conn.prepareStatement(lockAndCheckSql)) {
                stmt.setLong(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        referrerId = rs.getLong("referrer_id");
                        java.sql.Timestamp timestamp = rs.getTimestamp("created_at");
                        if (timestamp != null) {
                            createdAt = timestamp.toLocalDateTime();
                        }
                    } else {
                        LOGGER.warning("No pending referral found for user " + userId);
                        conn.rollback();
                        return;
                    }
                }
            }
            
            // FIXED: Check if referral has expired (30 days)
            if (createdAt != null && createdAt.plusDays(30).isBefore(LocalDateTime.now())) {
                String expireSql = "UPDATE referral_acceptances SET status = 'EXPIRED', processed_at = ? " +
                                 "WHERE referred_user_id = ? AND status = 'PENDING'";
                try (PreparedStatement stmt = conn.prepareStatement(expireSql)) {
                    stmt.setTimestamp(1, java.sql.Timestamp.valueOf(LocalDateTime.now()));
                    stmt.setLong(2, userId);
                    stmt.executeUpdate();
                }
                conn.commit();
                LOGGER.warning("Referral expired for user " + userId);
                return;
            }
            
            // FIXED: Check if user has already accepted any referral (double-check with lock)
            String checkAcceptedSql = "SELECT COUNT(*) FROM referral_acceptances WHERE referred_user_id = ? AND status = 'ACCEPTED'";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkAcceptedSql)) {
                checkStmt.setLong(1, userId);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        LOGGER.warning("User " + userId + " has already accepted a referral - cannot accept another");
                        conn.rollback();
                        return;
                    }
                }
            }
            
            // Update referral acceptance status
            String updateSql = "UPDATE referral_acceptances SET status = 'ACCEPTED', processed_at = ? WHERE referred_user_id = ? AND status = 'PENDING'";
            try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                stmt.setTimestamp(1, java.sql.Timestamp.valueOf(LocalDateTime.now()));
                stmt.setLong(2, userId);
                int updated = stmt.executeUpdate();
                if (updated == 0) {
                    LOGGER.warning("No pending referral to accept for user " + userId);
                    conn.rollback();
                    return;
                }
            }
            
            // Update user's referred_by field
            String updateUserSql = "UPDATE users SET referred_by = ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updateUserSql)) {
                stmt.setLong(1, referrerId);
                stmt.setLong(2, userId);
                stmt.executeUpdate();
            }
            
            // CRITICAL: Only NOW increment referrer's total_referrals count (when ACCEPTED)
            String updateReferrerSql = "UPDATE users SET total_referrals = COALESCE(total_referrals, 0) + 1, updated_at = ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updateReferrerSql)) {
                stmt.setTimestamp(1, java.sql.Timestamp.valueOf(LocalDateTime.now()));
                stmt.setLong(2, referrerId);
                stmt.executeUpdate();
            }
            
            // Create referral invitation record
            String insertInvitationSql = "INSERT INTO referral_invitations (referrer_id, referred_user_id, referral_code, status, created_at) VALUES (?, ?, (SELECT referral_code FROM users WHERE id = ?), 'ACTIVE', ?) ON CONFLICT (referrer_id, referred_user_id) DO NOTHING";
            try (PreparedStatement stmt = conn.prepareStatement(insertInvitationSql)) {
                stmt.setLong(1, referrerId);
                stmt.setLong(2, userId);
                stmt.setLong(3, referrerId);
                stmt.setTimestamp(4, java.sql.Timestamp.valueOf(LocalDateTime.now()));
                stmt.executeUpdate();
            }
            
            conn.commit();
            LOGGER.info("Referral ACCEPTED - referrer count incremented: User " + userId + " from referrer " + referrerId);
            
        } catch (SQLException e) {
            LOGGER.warning("Failed to accept referral: " + e.getMessage());
        }
    }
    
    public void rejectReferral(Long userId) {
        String sql = "UPDATE referral_acceptances SET status = 'REJECTED', processed_at = ? WHERE referred_user_id = ? AND status = 'PENDING'";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setTimestamp(1, java.sql.Timestamp.valueOf(LocalDateTime.now()));
            stmt.setLong(2, userId);
            
            int updated = stmt.executeUpdate();
            if (updated > 0) {
                LOGGER.info("Referral rejected for user " + userId);
            }
            
        } catch (SQLException e) {
            LOGGER.warning("Failed to reject referral: " + e.getMessage());
        }
    }
    
    public boolean hasPendingReferral(Long userId) {
        String sql = "SELECT COUNT(*) FROM referral_acceptances WHERE referred_user_id = ? AND status = 'PENDING'";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            
        } catch (SQLException e) {
            LOGGER.warning("Failed to check pending referral: " + e.getMessage());
        }
        
        return false;
    }
    
    public boolean hasAcceptedReferral(Long userId) {
        String sql = "SELECT COUNT(*) FROM referral_acceptances WHERE referred_user_id = ? AND status = 'ACCEPTED'";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            
        } catch (SQLException e) {
            LOGGER.warning("Failed to check accepted referral: " + e.getMessage());
        }
        
        return false;
    }
    
    public Long getAcceptedReferrerId(Long userId) {
        String sql = "SELECT referrer_id FROM referral_acceptances WHERE referred_user_id = ? AND status = 'ACCEPTED'";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getLong("referrer_id");
            }
            
        } catch (SQLException e) {
            LOGGER.warning("Failed to get accepted referrer: " + e.getMessage());
        }
        
        return null;
    }
}