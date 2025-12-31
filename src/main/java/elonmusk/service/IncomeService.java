package elonmusk.service;

import elonmusk.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.agroal.api.AgroalDataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;
import java.util.logging.Level;

@ApplicationScoped
public class IncomeService {
    
    private static final Logger LOGGER = Logger.getLogger(IncomeService.class.getName());
    
    @Inject
    AgroalDataSource dataSource;
    
    @Inject
    UserService userService;
    
    @Inject
    GiftCodeService giftCodeService;
    
    /**
     * Get total income collected from all sources
     */
    public BigDecimal getTotalIncomeCollected(Long userId) {
        BigDecimal referralEarnings = getReferralEarnings(userId);
        BigDecimal giftCodeEarnings = giftCodeService.getTotalGiftEarnings(userId);
        BigDecimal dailyIncomeCollected = getDailyIncomeCollected(userId);
        
        return referralEarnings.add(giftCodeEarnings).add(dailyIncomeCollected);
    }
    
    /**
     * Get income breakdown by source
     */
    public IncomeBreakdown getIncomeBreakdown(Long userId) {
        IncomeBreakdown breakdown = new IncomeBreakdown();
        breakdown.referralEarnings = getReferralEarnings(userId);
        breakdown.giftCodeEarnings = giftCodeService.getTotalGiftEarnings(userId);
        breakdown.dailyIncomeCollected = getDailyIncomeCollected(userId);
        breakdown.totalIncome = breakdown.referralEarnings
            .add(breakdown.giftCodeEarnings)
            .add(breakdown.dailyIncomeCollected);
        return breakdown;
    }
    
    /**
     * Get available daily income to collect
     */
    public BigDecimal getAvailableDailyIncome(Long userId) {
        String sql = "SELECT COALESCE(SUM(gift_amount), 0) FROM daily_gifts " +
                    "WHERE user_id = ? AND is_collected = false AND gift_date <= CURRENT_DATE";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error calculating available daily income", e);
        }
        return BigDecimal.ZERO;
    }
    
    private BigDecimal getReferralEarnings(Long userId) {
        User user = userService.findById(userId);
        return user != null && user.referralEarnings != null ? user.referralEarnings : BigDecimal.ZERO;
    }
    
    private BigDecimal getDailyIncomeCollected(Long userId) {
        // Use the optimized field first, fallback to calculation if needed
        String sql = "SELECT COALESCE(total_daily_income_collected, 0) FROM users WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    BigDecimal total = rs.getBigDecimal(1);
                    // If the field is null or zero, calculate from daily_gifts table
                    if (total.compareTo(BigDecimal.ZERO) == 0) {
                        return calculateDailyIncomeFromGifts(userId);
                    }
                    return total;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error getting daily income from users table", e);
            // Fallback to calculation
            return calculateDailyIncomeFromGifts(userId);
        }
        return BigDecimal.ZERO;
    }
    
    /**
     * Fallback method to calculate daily income from daily_gifts table
     */
    private BigDecimal calculateDailyIncomeFromGifts(Long userId) {
        String sql = "SELECT COALESCE(SUM(gift_amount), 0) FROM daily_gifts " +
                    "WHERE user_id = ? AND is_collected = true";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error calculating daily income from gifts", e);
        }
        return BigDecimal.ZERO;
    }
    
    /**
     * Income breakdown data structure
     */
    public static class IncomeBreakdown {
        public BigDecimal referralEarnings = BigDecimal.ZERO;
        public BigDecimal giftCodeEarnings = BigDecimal.ZERO;
        public BigDecimal dailyIncomeCollected = BigDecimal.ZERO;
        public BigDecimal availableDailyIncome = BigDecimal.ZERO;
        public BigDecimal totalIncome = BigDecimal.ZERO;
    }
}