package elonmusk.model;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class WithdrawalRequest {
    private static final Logger logger = Logger.getLogger(WithdrawalRequest.class.getName());
    
    public Long id;
    public Long userId;
    public BigDecimal amount;
    public String bankAccount;
    public String status; // PENDING, APPROVED, REJECTED
    public String adminNotes;
    public LocalDateTime createdAt;
    public LocalDateTime processedAt;
    
    public void save(Connection conn) throws SQLException {
        String sql = "INSERT INTO withdrawal_requests (user_id, amount, bank_account, status, created_at) VALUES (?, ?, ?, ?, ?) RETURNING id";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setBigDecimal(2, amount);
            stmt.setString(3, bankAccount);
            stmt.setString(4, status);
            stmt.setTimestamp(5, Timestamp.valueOf(createdAt));
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    this.id = rs.getLong("id");
                }
            }
        }
    }
    
    public static List<WithdrawalRequest> findAll(Connection conn) throws SQLException {
        String sql = "SELECT wr.*, u.name, u.phone_number FROM withdrawal_requests wr JOIN users u ON wr.user_id = u.id ORDER BY wr.created_at DESC";
        List<WithdrawalRequest> requests = new ArrayList<>();
        
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                WithdrawalRequest request = new WithdrawalRequest();
                request.id = rs.getLong("id");
                request.userId = rs.getLong("user_id");
                request.amount = rs.getBigDecimal("amount");
                request.bankAccount = rs.getString("bank_account");
                request.status = rs.getString("status");
                request.adminNotes = rs.getString("admin_notes");
                request.createdAt = rs.getTimestamp("created_at").toLocalDateTime();
                request.processedAt = rs.getTimestamp("processed_at") != null ? rs.getTimestamp("processed_at").toLocalDateTime() : null;
                requests.add(request);
            }
        }
        return requests;
    }
    
    public static void updateStatus(Connection conn, Long id, String status, String notes) throws SQLException {
        String sql = "UPDATE withdrawal_requests SET status = ?, admin_notes = ?, processed_at = ? WHERE id = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setString(2, notes);
            stmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setLong(4, id);
            stmt.executeUpdate();
        }
    }
}