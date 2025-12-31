package elonmusk.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.agroal.api.AgroalDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.time.LocalDateTime;

@ApplicationScoped
public class NotificationService {
    private static final Logger LOGGER = Logger.getLogger(NotificationService.class.getName());
    
    @Inject
    AgroalDataSource dataSource;
    
    public void createNotification(Long userId, String title, String message, String type) {
        String sql = "INSERT INTO notifications (user_id, title, message, type, created_at) VALUES (?, ?, ?, ?, ?)";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, userId);
            stmt.setString(2, title);
            stmt.setString(3, message);
            stmt.setString(4, type);
            stmt.setTimestamp(5, java.sql.Timestamp.valueOf(LocalDateTime.now()));
            
            stmt.executeUpdate();
            LOGGER.info("Notification created for user: " + userId);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error creating notification", e);
        }
    }
    
    public List<Notification> getUserNotifications(Long userId, int limit) {
        String sql = "SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT ?";
        List<Notification> notifications = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, userId);
            stmt.setInt(2, limit);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Notification notification = new Notification();
                    notification.id = rs.getLong("id");
                    notification.title = rs.getString("title");
                    notification.message = rs.getString("message");
                    notification.type = rs.getString("type");
                    notification.isRead = rs.getBoolean("is_read");
                    notification.createdAt = rs.getTimestamp("created_at").toLocalDateTime();
                    notifications.add(notification);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error getting user notifications", e);
        }
        
        return notifications;
    }
    
    public static class Notification {
        public Long id;
        public String title;
        public String message;
        public String type;
        public boolean isRead;
        public LocalDateTime createdAt;
    }
}