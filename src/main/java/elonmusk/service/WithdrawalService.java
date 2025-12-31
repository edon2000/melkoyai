package elonmusk.service;

import elonmusk.model.User;
import elonmusk.model.WithdrawalRequest;
import elonmusk.exception.ValidationException;
import elonmusk.exception.ServiceException;
import elonmusk.validation.InputValidator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import io.agroal.api.AgroalDataSource;

@ApplicationScoped
public class WithdrawalService {
    private static final Logger LOGGER = Logger.getLogger(WithdrawalService.class.getName());
    private static final BigDecimal MIN_WITHDRAWAL = new BigDecimal("5.00"); // $5.00 minimum
    private static final BigDecimal MAX_WITHDRAWAL = new BigDecimal("10000.00"); // $10,000 maximum
    

    
    @Inject
    AgroalDataSource dataSource;
    
    public WithdrawalRequest createWithdrawalRequest(User user, BigDecimal amount, String bankAccount) {
        validateWithdrawalRequest(user, amount, bankAccount);
        
        // Check 72-hour restriction
        if (hasRecentWithdrawal(user.id)) {
            throw new ValidationException("You can only make one withdrawal request every 72 hours. Please wait before making another request.");
        }
        
        try (Connection conn = dataSource.getConnection()) {
            WithdrawalRequest request = new WithdrawalRequest();
            request.userId = user.id;
            request.amount = amount;
            request.bankAccount = bankAccount.trim();
            request.status = "PENDING";
            request.createdAt = LocalDateTime.now();
            request.save(conn);
            
            LOGGER.info("Withdrawal request created: User " + user.id + " requested " + amount + " USD");
            return request;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error creating withdrawal request", e);
            throw new ServiceException("Unable to process withdrawal request: " + e.getMessage(), e);
        }
    }
    
    public List<WithdrawalRequest> getUserWithdrawalHistory(Long userId, int limit) {
        String sql = "SELECT * FROM withdrawal_requests WHERE user_id = ? ORDER BY created_at DESC LIMIT ?";
        List<WithdrawalRequest> history = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, userId);
            stmt.setInt(2, limit);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    WithdrawalRequest request = new WithdrawalRequest();
                    request.id = rs.getLong("id");
                    request.userId = rs.getLong("user_id");
                    request.amount = rs.getBigDecimal("amount");
                    request.bankAccount = rs.getString("bank_account");
                    request.status = rs.getString("status");
                    request.adminNotes = rs.getString("admin_notes");
                    request.createdAt = rs.getTimestamp("created_at").toLocalDateTime();
                    if (rs.getTimestamp("processed_at") != null) {
                        request.processedAt = rs.getTimestamp("processed_at").toLocalDateTime();
                    }
                    history.add(request);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error retrieving user withdrawal history", e);
            throw new ServiceException("Failed to retrieve withdrawal history: " + e.getMessage(), e);
        }
        
        return history;
    }
    
    private boolean hasRecentWithdrawal(Long userId) {
        String sql = "SELECT COUNT(*) FROM withdrawal_requests WHERE user_id = ? AND created_at > NOW() - INTERVAL '72 hours'";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error checking recent withdrawals", e);
            // If we can't check, allow the withdrawal to be safe
            return false;
        }
        
        return false;
    }
    
    public void approveWithdrawal(Long requestId) {
        if (requestId == null || requestId <= 0) {
            throw new ValidationException("Valid withdrawal request ID is required");
        }
        
        try (Connection conn = dataSource.getConnection()) {
            WithdrawalRequest.updateStatus(conn, requestId, "APPROVED", "Approved by admin");
            LOGGER.info("Withdrawal approved: " + requestId);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error approving withdrawal: " + requestId, e);
            throw new ServiceException("Failed to approve withdrawal: " + e.getMessage(), e);
        }
    }
    
    public void rejectWithdrawal(Long requestId, String reason) {
        if (requestId == null || requestId <= 0) {
            throw new ValidationException("Valid withdrawal request ID is required");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new ValidationException("Rejection reason is required");
        }
        
        try (Connection conn = dataSource.getConnection()) {
            WithdrawalRequest.updateStatus(conn, requestId, "REJECTED", reason.trim());
            LOGGER.info("Withdrawal rejected: " + requestId + " reason: " + reason);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error rejecting withdrawal: " + requestId, e);
            throw new ServiceException("Failed to reject withdrawal: " + e.getMessage(), e);
        }
    }
    
    public List<WithdrawalRequest> getAllRequests() {
        try (Connection conn = dataSource.getConnection()) {
            return WithdrawalRequest.findAll(conn);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error retrieving withdrawal requests", e);
            throw new ServiceException("Failed to retrieve withdrawal requests: " + e.getMessage(), e);
        }
    }
    
    private void validateWithdrawalRequest(User user, BigDecimal amount, String bankAccount) {
        if (user == null || user.id == null) {
            throw new ValidationException("Valid user is required");
        }
        
        if (amount == null) {
            throw new ValidationException("Amount is required");
        }
        
        if (amount.compareTo(MIN_WITHDRAWAL) < 0) {
            throw new ValidationException("Minimum withdrawal amount is $" + MIN_WITHDRAWAL + " USD");
        }
        
        if (amount.compareTo(MAX_WITHDRAWAL) > 0) {
            throw new ValidationException("Maximum withdrawal amount is $" + MAX_WITHDRAWAL + " USD");
        }
        
        if (bankAccount == null || bankAccount.trim().isEmpty()) {
            throw new ValidationException("Bank account number is required");
        }
        
        if (bankAccount.trim().length() < 10 || bankAccount.trim().length() > 20) {
            throw new ValidationException("Bank account number must be between 10 and 20 characters");
        }
        
        if (user.walletBalance == null || user.walletBalance.compareTo(amount) < 0) {
            throw new ValidationException("Insufficient balance. Available: $" + (user.walletBalance != null ? user.walletBalance : "0") + " USD");
        }
    }
}