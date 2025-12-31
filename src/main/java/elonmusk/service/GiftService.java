package elonmusk.service;

import elonmusk.model.DailyGift;
import elonmusk.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.agroal.api.AgroalDataSource;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@ApplicationScoped
public class GiftService {
    
    private static final Logger LOGGER = Logger.getLogger(GiftService.class.getName());
    
    @Inject
    AgroalDataSource dataSource;
    
    @Inject
    UserService userService;
    
    public List<DailyGift> getAvailableGifts(Long userId) throws SQLException {
        String sql = "SELECT * FROM daily_gifts WHERE user_id = ? AND is_collected = false AND gift_date <= CURRENT_DATE ORDER BY gift_date";
        List<DailyGift> gifts = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    gifts.add(mapResultSet(rs));
                }
            }
        }
        return gifts;
    }
    
    /**
     * Generate daily gifts from user's active investments
     * Only generates gifts if not already generated for current date
     * @param userId The user ID to generate gifts for
     * @throws SQLException if database error occurs
     */
    public void generateDailyGifts(Long userId) throws SQLException {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("Invalid user ID for daily gift generation");
        }
        
        // Generate gifts from user's daily investment returns
        // Only create gifts for active investments that haven't been processed today
        String sql = "INSERT INTO daily_gifts (user_id, gift_amount, gift_date, source, created_at) " +
                    "SELECT ui.user_id, ui.daily_return, CURRENT_DATE, 'INVESTMENT_RETURN', CURRENT_TIMESTAMP " +
                    "FROM user_investments ui " +
                    "WHERE ui.user_id = ? AND ui.status = 'ACTIVE' " +
                    "AND ui.daily_return > 0 " +
                    "AND NOT EXISTS (SELECT 1 FROM daily_gifts dg WHERE dg.user_id = ui.user_id AND dg.gift_date = CURRENT_DATE AND dg.source = 'INVESTMENT_RETURN')";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            int rowsInserted = stmt.executeUpdate();
            if (rowsInserted > 0) {
                LOGGER.info("Generated " + rowsInserted + " daily gift(s) for user " + userId);
            }
        } catch (SQLException e) {
            LOGGER.severe("Error generating daily gifts for user " + userId + ": " + e.getMessage());
            throw e;
        }
    }
    
    public boolean collectGift(Long userId, Long giftId) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Get gift details
                DailyGift gift = getGiftById(giftId);
                if (gift == null || !gift.userId.equals(userId) || gift.isCollected) {
                    conn.rollback();
                    return false;
                }
                
                // Mark gift as collected
                String updateGiftSql = "UPDATE daily_gifts SET is_collected = true, collected_at = CURRENT_TIMESTAMP WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(updateGiftSql)) {
                    stmt.setLong(1, giftId);
                    stmt.executeUpdate();
                }
                
                // Add to user wallet
                User user = userService.findById(userId);
                BigDecimal newBalance = user.walletBalance.add(gift.giftAmount);
                userService.updateWalletBalance(userId, newBalance);
                
                // ENHANCED: Update total_daily_income_collected for better performance
                String updateUserIncomeSql = "UPDATE users SET total_daily_income_collected = COALESCE(total_daily_income_collected, 0) + ? WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(updateUserIncomeSql)) {
                    stmt.setBigDecimal(1, gift.giftAmount);
                    stmt.setLong(2, userId);
                    stmt.executeUpdate();
                }
                
                conn.commit();
                LOGGER.info("Gift collected successfully: User " + userId + " collected $" + gift.giftAmount);
                return true;
                
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }
    
    public DailyGift getGiftById(Long giftId) throws SQLException {
        String sql = "SELECT * FROM daily_gifts WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, giftId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }
    
    private DailyGift mapResultSet(ResultSet rs) throws SQLException {
        DailyGift gift = new DailyGift();
        gift.id = rs.getLong("id");
        gift.userId = rs.getLong("user_id");
        gift.giftAmount = rs.getBigDecimal("gift_amount");
        gift.giftDate = rs.getDate("gift_date").toLocalDate();
        gift.isCollected = rs.getBoolean("is_collected");
        gift.collectedAt = rs.getTimestamp("collected_at") != null ? rs.getTimestamp("collected_at").toLocalDateTime() : null;
        gift.source = rs.getString("source");
        gift.createdAt = rs.getTimestamp("created_at").toLocalDateTime();
        return gift;
    }
}