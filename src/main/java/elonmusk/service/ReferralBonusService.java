package elonmusk.service;

import elonmusk.model.User;
import elonmusk.exception.ServiceException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.enterprise.inject.spi.CDI;
import io.agroal.api.AgroalDataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.logging.Logger;
import java.util.logging.Level;

@ApplicationScoped
public class ReferralBonusService {
    
    private static final Logger LOGGER = Logger.getLogger(ReferralBonusService.class.getName());
    private static final BigDecimal REFERRAL_RATE = new BigDecimal("0.10"); // 10%
    
    @Inject
    io.agroal.api.AgroalDataSource dataSource;
    
    @Inject
    UserService userService;
    
    @Inject
    ReferralInvitationService referralInvitationService;
    
    /**
     * Process referral bonus when user makes their FIRST deposit only
     * @param userId The user who made the deposit
     * @param amount The amount deposited
     */
    public void processReferralBonus(Long userId, BigDecimal amount) {
        if (userId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        
        try {
            // Check if user has accepted referral
            elonmusk.service.ReferralAcceptanceService acceptanceService = 
                CDI.current().select(elonmusk.service.ReferralAcceptanceService.class).get();
            
            if (!acceptanceService.hasAcceptedReferral(userId)) {
                LOGGER.info("Referral bonus skipped - referral not accepted for user: " + userId);
                return; // No bonus if referral not accepted
            }
            
            Long referrerId = acceptanceService.getAcceptedReferrerId(userId);
            if (referrerId == null) {
                LOGGER.info("Referral bonus skipped - no referrer found for user: " + userId);
                return;
            }
            
            User user = userService.findById(userId);
            if (user == null) {
                LOGGER.warning("User not found: " + userId);
                return;
            }
            
            // Check if this is the user's first approved deposit
            if (!isFirstApprovedDeposit(userId)) {
                LOGGER.info("Referral bonus skipped - not first approved deposit for user: " + userId);
                return;
            }
            
            User referrer = userService.findById(referrerId);
            if (referrer == null) {
                LOGGER.warning("Referrer not found for user: " + userId);
                return;
            }
            
            // Check if bonus has already been paid for this referral
            if (referralInvitationService.hasReferralBonusBeenPaid(referrer.id, userId)) {
                LOGGER.info("Referral bonus already paid for referrer " + referrer.id + " and user " + userId);
                return;
            }
            
            // Calculate 10% bonus
            BigDecimal bonus = amount.multiply(REFERRAL_RATE);
            
            // Update referrer's wallet and referral earnings
            updateReferrerBalance(referrer.id, bonus);
            
            // Record the referral bonus payment
            referralInvitationService.recordReferralBonus(referrer.id, userId, bonus, amount);
            
            // Log transaction for referral bonus
            logReferralTransaction(referrer.id, user.id, amount, bonus);
            
            LOGGER.info("FIRST DEPOSIT referral bonus processed: $" + bonus + " (10% of $" + amount + ") for referrer " + referrer.id + " from user " + userId);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing referral bonus", e);
        }
    }
    
    /**
     * Check if this is the user's first APPROVED deposit
     */
    private boolean isFirstApprovedDeposit(Long userId) {
        String sql = "SELECT COUNT(*) FROM deposit_requests WHERE user_id = ? AND status = 'APPROVED'";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, userId);
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int approvedCount = rs.getInt(1);
                    // This should be 1 (the current deposit being approved)
                    return approvedCount == 1;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error checking first approved deposit status", e);
        }
        
        return false;
    }
    
    private void updateReferrerBalance(Long referrerId, BigDecimal bonus) throws SQLException {
        String sql = "UPDATE users SET wallet_balance = wallet_balance + ?, referral_earnings = COALESCE(referral_earnings, 0) + ?, updated_at = ? WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setBigDecimal(1, bonus);
            stmt.setBigDecimal(2, bonus);
            stmt.setTimestamp(3, java.sql.Timestamp.valueOf(LocalDateTime.now()));
            stmt.setLong(4, referrerId);
            
            int updated = stmt.executeUpdate();
            if (updated > 0) {
                LOGGER.info("First deposit referral bonus credited: $" + bonus + " to user " + referrerId);
            }
        }
    }
    /**
     * Validate referral invitation during signup
     */
    public boolean validateReferralInvitation(String referralCode, User newUser) {
        return referralInvitationService.validateReferralInvitation(referralCode, newUser);
    }
    
    private void logReferralTransaction(Long referrerId, Long referredUserId, BigDecimal originalAmount, BigDecimal bonus) {
        String sql = "INSERT INTO transactions (user_id, type, category, amount, net_amount, description, related_user_id, status, processed_by, processed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, referrerId);
            stmt.setString(2, "REFERRAL");
            stmt.setString(3, "REFERRAL_BONUS");
            stmt.setBigDecimal(4, bonus);
            stmt.setBigDecimal(5, bonus);
            stmt.setString(6, "10% first deposit referral bonus from user " + referredUserId + " deposit of $" + originalAmount);
            stmt.setLong(7, referredUserId);
            stmt.setString(8, "COMPLETED");
            stmt.setString(9, "SYSTEM");
            stmt.setTimestamp(10, java.sql.Timestamp.valueOf(LocalDateTime.now()));
            
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to log referral transaction", e);
        }
    }
}