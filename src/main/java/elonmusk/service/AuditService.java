package elonmusk.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.agroal.api.AgroalDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.logging.Logger;
import java.util.logging.Level;

@ApplicationScoped
public class AuditService {
    private static final Logger LOGGER = Logger.getLogger(AuditService.class.getName());
    
    @Inject
    AgroalDataSource dataSource;
    
    public void logUserAction(Long userId, String action, String details, String ipAddress) {
        String sql = "INSERT INTO audit_logs (user_id, action, details, ip_address, created_at) VALUES (?, ?, ?, ?, ?)";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, userId);
            stmt.setString(2, action);
            stmt.setString(3, details);
            stmt.setString(4, ipAddress);
            stmt.setTimestamp(5, java.sql.Timestamp.valueOf(LocalDateTime.now()));
            
            stmt.executeUpdate();
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error logging audit action", e);
        }
    }
    
    public void logSecurityEvent(String event, String details, String ipAddress) {
        String sql = "INSERT INTO security_logs (event, details, ip_address, created_at) VALUES (?, ?, ?, ?)";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, event);
            stmt.setString(2, details);
            stmt.setString(3, ipAddress);
            stmt.setTimestamp(4, java.sql.Timestamp.valueOf(LocalDateTime.now()));
            
            stmt.executeUpdate();
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error logging security event", e);
        }
    }
    
    public void logAdminAction(String adminUser, String action, String details) {
        String sql = "INSERT INTO admin_logs (admin_user, action, details, created_at) VALUES (?, ?, ?, ?)";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, adminUser);
            stmt.setString(2, action);
            stmt.setString(3, details);
            stmt.setTimestamp(4, java.sql.Timestamp.valueOf(LocalDateTime.now()));
            
            stmt.executeUpdate();
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error logging admin action", e);
        }
    }
}