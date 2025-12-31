package elonmusk.service;

import elonmusk.model.DepositRequest;
import elonmusk.exception.ValidationException;
import elonmusk.exception.ServiceException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.logging.Level;
import io.agroal.api.AgroalDataSource;

@ApplicationScoped
public class DepositService {
    
    private static final Logger LOGGER = Logger.getLogger(DepositService.class.getName());
    
    @Inject
    AgroalDataSource dataSource;
    
    @Inject
    UserService userService;
    
    @Inject
    ReferralBonusService referralBonusService;
    
    public boolean createDepositRequest(DepositRequest deposit) {
        // Check if transaction ID already exists
        if (isTransactionIdExists(deposit.transactionId)) {
            throw new ValidationException("Transaction ID already exists. Each transaction ID can only be used once.");
        }
        
        String sql = "INSERT INTO deposit_requests (user_id, transaction_id, payment_method, amount, status, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, deposit.userId);
            stmt.setString(2, deposit.transactionId);
            stmt.setString(3, deposit.paymentMethod);
            stmt.setBigDecimal(4, deposit.amount);
            stmt.setString(5, deposit.status);
            stmt.setTimestamp(6, java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()));
            
            int result = stmt.executeUpdate();
            
            if (result > 0) {
                LOGGER.info("Deposit request created: User " + deposit.userId + " Amount: $" + deposit.amount + " Method: " + deposit.paymentMethod);
                return true;
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error creating deposit request", e);
            throw new ServiceException("Failed to create deposit request");
        }
        
        return false;
    }
    
    /**
     * Check if transaction ID already exists in database
     */
    private boolean isTransactionIdExists(String transactionId) {
        String sql = "SELECT COUNT(*) FROM deposit_requests WHERE transaction_id = ?";
        
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, transactionId);
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error checking transaction ID", e);
        }
        
        return false;
    }
    
    /**
     * Get deposit statistics for user
     */
    public java.util.Map<String, Object> getDepositStats(Long userId) {
        String sql = "SELECT status, COUNT(*) as count, COALESCE(SUM(amount), 0) as total FROM deposit_requests WHERE user_id = ? GROUP BY status";
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        
        // Initialize with zeros
        stats.put("totalPending", java.math.BigDecimal.ZERO);
        stats.put("totalApproved", java.math.BigDecimal.ZERO);
        stats.put("totalRejected", java.math.BigDecimal.ZERO);
        stats.put("countPending", 0);
        stats.put("countApproved", 0);
        stats.put("countRejected", 0);
        
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, userId);
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String status = rs.getString("status");
                    int count = rs.getInt("count");
                    java.math.BigDecimal total = rs.getBigDecimal("total");
                    
                    switch (status) {
                        case "PENDING":
                            stats.put("totalPending", total);
                            stats.put("countPending", count);
                            break;
                        case "APPROVED":
                            stats.put("totalApproved", total);
                            stats.put("countApproved", count);
                            break;
                        case "REJECTED":
                            stats.put("totalRejected", total);
                            stats.put("countRejected", count);
                            break;
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error getting deposit stats", e);
        }
        
        return stats;
    }
    
    public List<DepositRequest> getUserDeposits(Long userId) {
        String sql = "SELECT * FROM deposit_requests WHERE user_id = ? ORDER BY created_at DESC LIMIT 20";
        List<DepositRequest> deposits = new ArrayList<>();
        
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, userId);
            
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    DepositRequest deposit = new DepositRequest();
                    deposit.id = rs.getLong("id");
                    deposit.userId = rs.getLong("user_id");
                    deposit.transactionId = rs.getString("transaction_id");
                    deposit.paymentMethod = rs.getString("payment_method");
                    deposit.amount = rs.getBigDecimal("amount");
                    deposit.status = rs.getString("status");
                    deposit.adminNotes = rs.getString("admin_notes");
                    
                    // Safe null handling for timestamps
                    java.sql.Timestamp createdTs = rs.getTimestamp("created_at");
                    if (createdTs != null) {
                        deposit.createdAt = createdTs.toLocalDateTime();
                    }
                    
                    java.sql.Timestamp processedTs = rs.getTimestamp("processed_at");
                    if (processedTs != null) {
                        deposit.processedAt = processedTs.toLocalDateTime();
                    }
                    
                    deposits.add(deposit);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error getting user deposits", e);
        }
        
        return deposits;
    }
    
    public boolean approveDepositWithAmount(Long depositId, BigDecimal amount) {
        String selectSql = "SELECT * FROM deposit_requests WHERE id = ?";
        String updateSql = "UPDATE deposit_requests SET status = 'APPROVED', amount = ?, admin_notes = ?, processed_at = ? WHERE id = ?";
        
        try (java.sql.Connection conn = dataSource.getConnection()) {
            DepositRequest deposit;
            
            // Get deposit details
            try (java.sql.PreparedStatement stmt = conn.prepareStatement(selectSql)) {
                stmt.setLong(1, depositId);
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        throw new ValidationException("Deposit request not found");
                    }
                    
                    deposit = new DepositRequest();
                    deposit.id = rs.getLong("id");
                    deposit.userId = rs.getLong("user_id");
                    deposit.amount = rs.getBigDecimal("amount");
                    deposit.paymentMethod = rs.getString("payment_method");
                    deposit.transactionId = rs.getString("transaction_id");
                    deposit.status = rs.getString("status");
                }
            }
            
            if (!"PENDING".equals(deposit.status)) {
                throw new ValidationException("Only pending deposits can be approved");
            }
            
            // Update deposit status with admin-verified amount
            try (java.sql.PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                stmt.setBigDecimal(1, amount);
                stmt.setString(2, "Approved by admin with amount: $" + amount);
                stmt.setTimestamp(3, java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()));
                stmt.setLong(4, depositId);
                stmt.executeUpdate();
            }
            
            // Credit user wallet with admin-verified amount
            userService.updateWalletBalance(deposit.userId, amount);
            
            // Process referral bonus (10% of deposit amount)
            referralBonusService.processReferralBonus(deposit.userId, amount);
            
            LOGGER.info("Deposit approved with amount: ID " + depositId + " Amount: $" + amount);
            return true;
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error approving deposit with amount", e);
            throw new ServiceException("Failed to approve deposit");
        }
    }
    
    public boolean approveDeposit(Long depositId, String adminNotes) {
        String selectSql = "SELECT * FROM deposit_requests WHERE id = ?";
        String updateSql = "UPDATE deposit_requests SET status = 'APPROVED', admin_notes = ?, processed_at = ? WHERE id = ?";
        
        try (java.sql.Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false); // Start transaction
            
            try {
                DepositRequest deposit;
                
                // Get deposit details
                try (java.sql.PreparedStatement stmt = conn.prepareStatement(selectSql)) {
                    stmt.setLong(1, depositId);
                    try (java.sql.ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new ValidationException("Deposit request not found");
                        }
                        
                        deposit = new DepositRequest();
                        deposit.id = rs.getLong("id");
                        deposit.userId = rs.getLong("user_id");
                        deposit.amount = rs.getBigDecimal("amount");
                        deposit.paymentMethod = rs.getString("payment_method");
                        deposit.transactionId = rs.getString("transaction_id");
                        deposit.status = rs.getString("status");
                    }
                }
                
                if (!"PENDING".equals(deposit.status)) {
                    throw new ValidationException("Only pending deposits can be approved");
                }
                
                // Update deposit status
                try (java.sql.PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                    stmt.setString(1, adminNotes);
                    stmt.setTimestamp(2, java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()));
                    stmt.setLong(3, depositId);
                    stmt.executeUpdate();
                }
                
                // Get current user balance
                elonmusk.model.User user = userService.findById(deposit.userId);
                if (user == null) {
                    throw new ValidationException("User not found");
                }
                
                BigDecimal newBalance = user.walletBalance.add(deposit.amount);
                
                // Credit user wallet
                userService.updateWalletBalance(deposit.userId, newBalance);
                
                // AUTOMATIC 10% REFERRAL BONUS: Process referral bonus when deposit is approved
                referralBonusService.processReferralBonus(deposit.userId, deposit.amount);
                
                conn.commit(); // Commit transaction
                LOGGER.info("Deposit approved: ID " + depositId + " Amount: $" + deposit.amount + " - Referral bonus processed");
                return true;
                
            } catch (Exception e) {
                conn.rollback(); // Rollback on error
                throw e;
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error approving deposit", e);
            throw new ServiceException("Failed to approve deposit");
        }
    }
    
    public boolean rejectDeposit(Long depositId, String rejectionReason) {
        String sql = "UPDATE deposit_requests SET status = 'REJECTED', admin_notes = ?, processed_at = ? WHERE id = ? AND status = 'PENDING'";
        
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, rejectionReason);
            stmt.setTimestamp(2, java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()));
            stmt.setLong(3, depositId);
            
            int updated = stmt.executeUpdate();
            if (updated == 0) {
                throw new ValidationException("Deposit not found or already processed");
            }
            
            LOGGER.info("Deposit rejected: ID " + depositId + " Reason: " + rejectionReason);
            return true;
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error rejecting deposit", e);
            throw new ServiceException("Failed to reject deposit");
        }
    }
    
    public List<DepositRequest> getAllPendingDeposits() {
        String sql = "SELECT dr.*, u.name as user_name, u.phone_number FROM deposit_requests dr " +
                    "JOIN users u ON dr.user_id = u.id WHERE dr.status = 'PENDING' ORDER BY dr.created_at ASC";
        List<DepositRequest> deposits = new ArrayList<>();
        
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(sql);
             java.sql.ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                DepositRequest deposit = new DepositRequest();
                deposit.id = rs.getLong("id");
                deposit.userId = rs.getLong("user_id");
                deposit.amount = rs.getBigDecimal("amount");
                deposit.paymentMethod = rs.getString("payment_method");
                deposit.transactionId = rs.getString("transaction_id");
                deposit.status = rs.getString("status");
                deposit.adminNotes = rs.getString("admin_notes");
                
                // Safe null handling
                java.sql.Timestamp createdTs = rs.getTimestamp("created_at");
                if (createdTs != null) {
                    deposit.createdAt = createdTs.toLocalDateTime();
                }
                
                deposit.userName = rs.getString("user_name");
                deposit.userPhone = rs.getString("phone_number");
                deposits.add(deposit);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error getting pending deposits", e);
        }
        
        return deposits;
    }
    
    public List<DepositRequest> getAllDeposits() {
        String sql = "SELECT dr.*, u.name as user_name, u.phone_number FROM deposit_requests dr " +
                    "JOIN users u ON dr.user_id = u.id ORDER BY dr.created_at DESC";
        List<DepositRequest> deposits = new ArrayList<>();
        
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(sql);
             java.sql.ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                DepositRequest deposit = new DepositRequest();
                deposit.id = rs.getLong("id");
                deposit.userId = rs.getLong("user_id");
                deposit.amount = rs.getBigDecimal("amount");
                deposit.paymentMethod = rs.getString("payment_method");
                deposit.transactionId = rs.getString("transaction_id");
                deposit.status = rs.getString("status");
                deposit.adminNotes = rs.getString("admin_notes");
                
                // Safe null handling
                java.sql.Timestamp createdTs = rs.getTimestamp("created_at");
                if (createdTs != null) {
                    deposit.createdAt = createdTs.toLocalDateTime();
                }
                
                deposit.userName = rs.getString("user_name");
                deposit.userPhone = rs.getString("phone_number");
                deposits.add(deposit);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error getting all deposits", e);
        }
        
        return deposits;
    }
    
    /**
     * Check if this is the user's first deposit (before approval)
     */
    private boolean isUserFirstDeposit(Long userId) {
        String sql = "SELECT COUNT(*) FROM deposit_requests WHERE user_id = ? AND status = 'APPROVED'";
        
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, userId);
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) == 0; // True if no approved deposits exist
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error checking first deposit status", e);
        }
        
        return false;
    }
}