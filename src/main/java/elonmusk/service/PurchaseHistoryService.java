package elonmusk.service;

import elonmusk.model.PurchaseHistory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.agroal.api.AgroalDataSource;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@ApplicationScoped
public class PurchaseHistoryService {
    
    private static final Logger LOGGER = Logger.getLogger(PurchaseHistoryService.class.getName());
    
    @Inject
    AgroalDataSource dataSource;
    
    public void recordPurchase(Long userId, Long productId, String productName, String productImage, BigDecimal amount) throws SQLException {
        String sql = "INSERT INTO purchase_history (user_id, product_id, product_name, product_image, amount, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setObject(2, productId);
            stmt.setString(3, productName);
            stmt.setString(4, productImage);
            stmt.setBigDecimal(5, amount);
            stmt.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
            stmt.executeUpdate();
        }
    }
    
    public List<PurchaseHistory> getUserPurchases(Long userId) throws SQLException {
        String sql = "SELECT * FROM purchase_history WHERE user_id = ? ORDER BY created_at DESC";
        List<PurchaseHistory> purchases = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    purchases.add(mapResultSet(rs));
                }
            }
        }
        return purchases;
    }
    
    public BigDecimal getTotalPurchaseAmount(Long userId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount), 0) as total FROM purchase_history WHERE user_id = ?";
        
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
    }
    
    public int getPurchaseCount(Long userId) throws SQLException {
        String sql = "SELECT COUNT(*) as count FROM purchase_history WHERE user_id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count");
                }
            }
        }
        return 0;
    }
    
    private PurchaseHistory mapResultSet(ResultSet rs) throws SQLException {
        PurchaseHistory purchase = new PurchaseHistory();
        purchase.id = rs.getLong("id");
        purchase.userId = rs.getLong("user_id");
        purchase.productId = rs.getObject("product_id", Long.class);
        purchase.productName = rs.getString("product_name");
        purchase.productImage = rs.getString("product_image");
        purchase.amount = rs.getBigDecimal("amount");
        purchase.purchaseType = rs.getString("purchase_type");
        purchase.status = rs.getString("status");
        purchase.createdAt = rs.getTimestamp("created_at").toLocalDateTime();
        return purchase;
    }
}