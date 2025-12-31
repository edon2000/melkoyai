package elonmusk.service;

import elonmusk.exception.ValidationException;
import elonmusk.exception.ServiceException;
import elonmusk.validation.InputValidator;
import elonmusk.filter.SecurityUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.agroal.api.AgroalDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.logging.Level;

@ApplicationScoped
public class SecurityService {
    private static final Logger logger = Logger.getLogger(SecurityService.class.getName());
    
    @Inject
    AgroalDataSource dataSource;
    
    public void createPasswordResetRequest(String phoneNumber, String newPassword) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            throw new ValidationException("Phone number is required");
        }
        
        if (newPassword == null || newPassword.trim().isEmpty()) {
            throw new ValidationException("New password is required");
        }
        
        // Validate phone number format
        InputValidator.validatePhoneNumber(phoneNumber);
        
        // Validate password strength
        InputValidator.validatePassword(newPassword);
        
        // Sanitize inputs
        phoneNumber = SecurityUtil.sanitizeInput(phoneNumber.trim());
        newPassword = SecurityUtil.sanitizeInput(newPassword.trim());
        
        // Hash the new password
        String hashedPassword = org.mindrot.jbcrypt.BCrypt.hashpw(newPassword, org.mindrot.jbcrypt.BCrypt.gensalt());
        
        String sql = "INSERT INTO password_reset_requests (phone_number, new_password, status, created_at, expires_at) VALUES (?, ?, 'PENDING', ?, ?)";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expires = now.plusHours(24); // Expires in 24 hours
            
            stmt.setString(1, phoneNumber);
            stmt.setString(2, hashedPassword);
            stmt.setTimestamp(3, java.sql.Timestamp.valueOf(now));
            stmt.setTimestamp(4, java.sql.Timestamp.valueOf(expires));
            
            int result = stmt.executeUpdate();
            
            if (result > 0) {
                logger.info("Password reset request created for phone: " + phoneNumber);
            } else {
                throw new ServiceException("Failed to create password reset request");
            }
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error creating password reset request", e);
            throw new ServiceException("Failed to create password reset request");
        }
    }
    
    public List<elonmusk.model.PasswordResetRequest> getPendingResetRequests() {
        String sql = "SELECT * FROM password_reset_requests WHERE status = 'PENDING' ORDER BY created_at ASC";
        List<elonmusk.model.PasswordResetRequest> requests = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             java.sql.ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                elonmusk.model.PasswordResetRequest request = new elonmusk.model.PasswordResetRequest();
                request.id = rs.getLong("id");
                request.phoneNumber = rs.getString("phone_number");
                request.status = rs.getString("status");
                request.createdAt = rs.getTimestamp("created_at").toLocalDateTime();
                request.expiresAt = rs.getTimestamp("expires_at").toLocalDateTime();
                requests.add(request);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error getting pending reset requests", e);
        }
        
        return requests;
    }
    
    public boolean approvePasswordReset(Long requestId, String adminUser) {
        String selectSql = "SELECT * FROM password_reset_requests WHERE id = ? AND status = 'PENDING'";
        String updateRequestSql = "UPDATE password_reset_requests SET status = 'APPROVED', processed_at = ?, processed_by = ? WHERE id = ?";
        String updateUserSql = "UPDATE users SET password = ? WHERE phone_number = ?";
        
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            
            String phoneNumber;
            String newPassword;
            
            // Get reset request details
            try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
                stmt.setLong(1, requestId);
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        throw new ValidationException("Reset request not found or already processed");
                    }
                    phoneNumber = rs.getString("phone_number");
                    newPassword = rs.getString("new_password");
                }
            }
            
            // Update user password
            try (PreparedStatement stmt = conn.prepareStatement(updateUserSql)) {
                stmt.setString(1, newPassword);
                stmt.setString(2, phoneNumber);
                int updated = stmt.executeUpdate();
                if (updated == 0) {
                    throw new ValidationException("User not found for phone number: " + phoneNumber);
                }
            }
            
            // Update reset request status
            try (PreparedStatement stmt = conn.prepareStatement(updateRequestSql)) {
                stmt.setTimestamp(1, java.sql.Timestamp.valueOf(LocalDateTime.now()));
                stmt.setString(2, adminUser);
                stmt.setLong(3, requestId);
                stmt.executeUpdate();
            }
            
            conn.commit();
            logger.info("Password reset approved for phone: " + phoneNumber + " by admin: " + adminUser);
            return true;
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error approving password reset", e);
            throw new ServiceException("Failed to approve password reset");
        }
    }
    
    public void logSecurityEvent(String event, String details, String ipAddress) {
        String sql = "INSERT INTO security_logs (event, details, ip_address, created_at) VALUES (?, ?, ?::inet, ?)";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, event);
            stmt.setString(2, details);
            stmt.setString(3, ipAddress);
            stmt.setTimestamp(4, java.sql.Timestamp.valueOf(LocalDateTime.now()));
            
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to log security event", e);
        }
    }
    
    public static class PasswordResetRequest {
        public Long id;
        public String phoneNumber;
        public String status;
        public LocalDateTime createdAt;
        public LocalDateTime expiresAt;
        public LocalDateTime processedAt;
        public String processedBy;
    }
}