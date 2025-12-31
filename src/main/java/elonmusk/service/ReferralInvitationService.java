package elonmusk.service;

import elonmusk.model.User;
import elonmusk.exception.ValidationException;
import elonmusk.exception.ServiceException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.logging.Logger;
import java.util.logging.Level;

@ApplicationScoped
public class ReferralInvitationService {
    
    private static final Logger LOGGER = Logger.getLogger(ReferralInvitationService.class.getName());
    
    @Inject
    DataSource dataSource;
    
    @Inject
    UserService userService;
    
    /**
     * Validate referral invitation when user signs up with referral code
     * Comprehensive validation with proper error handling
     * NOTE: This only creates the referral relationship, count is NOT incremented yet
     */
    public boolean validateReferralInvitation(String referralCode, User newUser) {
        // Input validation
        if (referralCode == null || referralCode.trim().isEmpty()) {
            LOGGER.fine("Referral code validation skipped: empty code");
            return false;
        }
        
        if (newUser == null || newUser.id == null) {
            throw new ValidationException("Valid user required for referral validation");
        }
        
        // Sanitize and normalize referral code
        referralCode = referralCode.trim().toUpperCase();
        
        // Validate referral code format (alphanumeric, reasonable length)
        if (!referralCode.matches("^[A-Z0-9]{3,20}$")) {
            LOGGER.warning("Invalid referral code format: " + referralCode);
            return false;
        }
        
        try {
            // Find referrer by code
            User referrer = userService.findByReferralCode(referralCode);
            if (referrer == null) {
                LOGGER.warning("Invalid referral code used: " + referralCode);
                return false;
            }
            
            // Prevent self-referral
            if (referrer.id.equals(newUser.id)) {
                LOGGER.warning("User cannot refer themselves: " + newUser.id);
                return false;
            }
            
            // Check if user is already referred by someone else
            if (newUser.referredBy != null && !newUser.referredBy.equals(referrer.id)) {
                LOGGER.warning("User " + newUser.id + " is already referred by user " + newUser.referredBy);
                return false;
            }
            
            // Update new user's referredBy field
            updateUserReferral(newUser.id, referrer.id);
            
            // Create referral invitation record
            createReferralRecord(referrer.id, newUser.id, referralCode);
            
            // DO NOT increment referrer count here - only when referral is ACCEPTED
            
            LOGGER.info("Referral invitation validated (pending acceptance): User " + newUser.id + " referred by " + referrer.id);
            return true;
            
        } catch (ValidationException e) {
            LOGGER.log(Level.WARNING, "Validation exception in referral invitation: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error validating referral invitation", e);
            return false;
        }
    }
    
    /**
     * Check if user has made their first deposit (for referral bonus eligibility)
     */
    public boolean isFirstDeposit(Long userId) {
        String sql = "SELECT COUNT(*) FROM deposit_requests WHERE user_id = ? AND status = 'APPROVED'";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) == 0; // True if no approved deposits exist
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error checking first deposit status", e);
        }
        
        return false;
    }
    
    /**
     * Check if referral bonus has already been paid for this user
     */
    public boolean hasReferralBonusBeenPaid(Long referrerId, Long referredUserId) {
        String sql = "SELECT COUNT(*) FROM referral_bonuses WHERE referrer_id = ? AND referred_user_id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, referrerId);
            stmt.setLong(2, referredUserId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error checking referral bonus status", e);
        }
        
        return false;
    }
    
    /**
     * Record referral bonus payment
     */
    public void recordReferralBonus(Long referrerId, Long referredUserId, java.math.BigDecimal bonusAmount, java.math.BigDecimal depositAmount) {
        String sql = "INSERT INTO referral_bonuses (referrer_id, referred_user_id, bonus_amount, deposit_amount, created_at) VALUES (?, ?, ?, ?, ?)";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, referrerId);
            stmt.setLong(2, referredUserId);
            stmt.setBigDecimal(3, bonusAmount);
            stmt.setBigDecimal(4, depositAmount);
            stmt.setTimestamp(5, java.sql.Timestamp.valueOf(LocalDateTime.now()));
            
            stmt.executeUpdate();
            LOGGER.info("Referral bonus recorded: $" + bonusAmount + " for referrer " + referrerId);
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error recording referral bonus", e);
            throw new ServiceException("Failed to record referral bonus");
        }
    }
    
    private void updateUserReferral(Long userId, Long referrerId) throws SQLException {
        String sql = "UPDATE users SET referred_by = ?, updated_at = ? WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, referrerId);
            stmt.setTimestamp(2, java.sql.Timestamp.valueOf(LocalDateTime.now()));
            stmt.setLong(3, userId);
            
            int updated = stmt.executeUpdate();
            if (updated == 0) {
                throw new ServiceException("Failed to update user referral");
            }
        }
    }
    
    private void createReferralRecord(Long referrerId, Long referredUserId, String referralCode) throws SQLException {
        String sql = "INSERT INTO referral_invitations (referrer_id, referred_user_id, referral_code, status, created_at) VALUES (?, ?, ?, ?, ?)";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, referrerId);
            stmt.setLong(2, referredUserId);
            stmt.setString(3, referralCode);
            stmt.setString(4, "ACTIVE");
            stmt.setTimestamp(5, java.sql.Timestamp.valueOf(LocalDateTime.now()));
            
            stmt.executeUpdate();
        }
    }
}