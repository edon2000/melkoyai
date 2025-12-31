package elonmusk.service;

import elonmusk.model.User;
import elonmusk.exception.ValidationException;
import elonmusk.exception.ServiceException;
import elonmusk.filter.SecurityUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.logging.Level;
import io.agroal.api.AgroalDataSource;

@ApplicationScoped
public class ReferralRewardService {
    private static final Logger LOGGER = Logger.getLogger(ReferralRewardService.class.getName());
    
    // Referral reward rates
    private static final BigDecimal REFERRAL_BONUS_RATE = new BigDecimal("0.10"); // 10% of investment
    private static final BigDecimal SIGNUP_BONUS = new BigDecimal("0.032"); // $0.032 signup bonus (5 ETB / 155)
    private static final BigDecimal FIRST_INVESTMENT_BONUS = new BigDecimal("0.065"); // $0.065 first investment bonus (10 ETB / 155)
    
    @Inject
    AgroalDataSource dataSource;
    
    @Inject
    User userModel;
    
    /**
     * Process referral signup bonus when new user registers
     */
    public void processSignupBonus(User referrer, User newUser) {
        if (referrer == null || newUser == null) {
            return;
        }
        
        try {
            // Give signup bonus to referrer
            updateUserBalance(referrer.id, SIGNUP_BONUS, "REFERRAL_SIGNUP", 
                "Signup bonus for referring " + newUser.phoneNumber);
            
            // Update referrer's referral count and earnings
            updateReferralStats(referrer.id, SIGNUP_BONUS);
            
            LOGGER.info("Signup bonus processed: $" + SIGNUP_BONUS + " for user " + referrer.id);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing signup bonus", e);
            throw new ServiceException("Failed to process signup bonus", e);
        }
    }
    
    /**
     * Process investment referral bonus when referred user makes investment
     */
    public void processInvestmentReferralBonus(Long userId, BigDecimal investmentAmount) {
        try {
            // Get user's referrer
            User referrer = getReferrer(userId);
            if (referrer == null) {
                return; // No referrer, no bonus
            }
            
            // Calculate 10% referral bonus
            BigDecimal bonus = investmentAmount.multiply(REFERRAL_BONUS_RATE);
            
            // Give investment bonus to referrer
            updateUserBalance(referrer.id, bonus, "REFERRAL_INVESTMENT", 
                "Investment referral bonus: 10% of $" + investmentAmount);
            
            // Update referrer's referral earnings
            updateReferralStats(referrer.id, bonus);
            
            // Check if this is the referred user's first investment
            if (isFirstInvestment(userId)) {
                // Give first investment bonus to referrer
                updateUserBalance(referrer.id, FIRST_INVESTMENT_BONUS, "REFERRAL_FIRST_INVESTMENT", 
                    "First investment bonus for referred user");
                updateReferralStats(referrer.id, FIRST_INVESTMENT_BONUS);
                
                LOGGER.info("First investment bonus processed: $" + FIRST_INVESTMENT_BONUS + " for user " + referrer.id);
            }
            
            LOGGER.info("Investment referral bonus processed: $" + bonus + " for user " + referrer.id);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing investment referral bonus", e);
            throw new ServiceException("Failed to process investment referral bonus", e);
        }
    }
    
    /**
     * Get user's referral statistics
     */
    public ReferralStats getUserReferralStats(Long userId) {
        String sql = "SELECT total_referrals, referral_earnings FROM users WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    ReferralStats stats = new ReferralStats();
                    stats.totalReferrals = rs.getInt("total_referrals");
                    stats.totalEarnings = rs.getBigDecimal("referral_earnings");
                    stats.referredUsers = getReferredUsers(userId);
                    return stats;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error getting referral stats", e);
        }
        
        return new ReferralStats();
    }
    
    /**
     * Get list of users referred by this user
     */
    public List<ReferredUser> getReferredUsers(Long userId) {
        String sql = "SELECT id, name, phone_number, created_at, total_invested FROM users WHERE referred_by = ? ORDER BY created_at DESC";
        List<ReferredUser> referredUsers = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ReferredUser user = new ReferredUser();
                    user.id = rs.getLong("id");
                    user.name = rs.getString("name");
                    user.phoneNumber = rs.getString("phone_number");
                    user.joinedAt = rs.getTimestamp("created_at").toLocalDateTime();
                    user.totalInvested = rs.getBigDecimal("total_invested");
                    referredUsers.add(user);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error getting referred users", e);
        }
        
        return referredUsers;
    }
    
    /**
     * Get user's reward history
     */
    public List<RewardHistory> getUserRewardHistory(Long userId, int limit) {
        String sql = "SELECT * FROM transactions WHERE user_id = ? AND type IN ('REFERRAL', 'BONUS') ORDER BY created_at DESC LIMIT ?";
        List<RewardHistory> history = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, userId);
            stmt.setInt(2, limit);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    RewardHistory reward = new RewardHistory();
                    reward.id = rs.getLong("id");
                    reward.type = rs.getString("category");
                    reward.amount = rs.getBigDecimal("amount");
                    reward.description = rs.getString("description");
                    reward.createdAt = rs.getTimestamp("created_at").toLocalDateTime();
                    history.add(reward);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error getting reward history", e);
        }
        
        return history;
    }
    
    private User getReferrer(Long userId) {
        String sql = "SELECT u2.* FROM users u1 JOIN users u2 ON u1.referred_by = u2.id WHERE u1.id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    User referrer = new User();
                    referrer.id = rs.getLong("id");
                    referrer.name = rs.getString("name");
                    referrer.phoneNumber = rs.getString("phone_number");
                    referrer.walletBalance = rs.getBigDecimal("wallet_balance");
                    referrer.referralEarnings = rs.getBigDecimal("referral_earnings");
                    return referrer;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting referrer", e);
        }
        
        return null;
    }
    
    private boolean isFirstInvestment(Long userId) {
        String sql = "SELECT COUNT(*) FROM investments WHERE user_id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) == 1; // First investment if count is 1
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error checking first investment", e);
        }
        
        return false;
    }
    
    private void updateUserBalance(Long userId, BigDecimal amount, String category, String description) {
        String updateUserSql = "UPDATE users SET wallet_balance = wallet_balance + ?, total_earned = total_earned + ? WHERE id = ?";
        String insertTransactionSql = "INSERT INTO transactions (user_id, type, category, amount, net_amount, description, status, processed_by, processed_at) VALUES (?, 'REFERRAL', ?, ?, ?, ?, 'COMPLETED', 'SYSTEM', ?)";
        
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            
            try (PreparedStatement userStmt = conn.prepareStatement(updateUserSql);
                 PreparedStatement transStmt = conn.prepareStatement(insertTransactionSql)) {
                
                // Update user balance
                userStmt.setBigDecimal(1, amount);
                userStmt.setBigDecimal(2, amount);
                userStmt.setLong(3, userId);
                userStmt.executeUpdate();
                
                // Log transaction
                transStmt.setLong(1, userId);
                transStmt.setString(2, category);
                transStmt.setBigDecimal(3, amount);
                transStmt.setBigDecimal(4, amount);
                transStmt.setString(5, description);
                transStmt.setTimestamp(6, java.sql.Timestamp.valueOf(LocalDateTime.now()));
                transStmt.executeUpdate();
                
                conn.commit();
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error updating user balance", e);
            throw new ServiceException("Failed to update user balance", e);
        }
    }
    
    private void updateReferralStats(Long userId, BigDecimal amount) {
        String sql = "UPDATE users SET referral_earnings = referral_earnings + ? WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setBigDecimal(1, amount);
            stmt.setLong(2, userId);
            stmt.executeUpdate();
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error updating referral stats", e);
            throw new ServiceException("Failed to update referral stats", e);
        }
    }
    
    // Data classes for referral system
    public static class ReferralStats {
        public int totalReferrals = 0;
        public BigDecimal totalEarnings = BigDecimal.ZERO;
        public List<ReferredUser> referredUsers = new ArrayList<>();
    }
    
    public static class ReferredUser {
        public Long id;
        public String name;
        public String phoneNumber;
        public LocalDateTime joinedAt;
        public BigDecimal totalInvested = BigDecimal.ZERO;
    }
    
    public static class RewardHistory {
        public Long id;
        public String type;
        public BigDecimal amount;
        public String description;
        public LocalDateTime createdAt;
    }
}