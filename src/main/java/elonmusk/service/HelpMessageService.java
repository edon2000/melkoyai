package elonmusk.service;

import elonmusk.model.HelpMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;

@ApplicationScoped
public class HelpMessageService {
    
    private static final Logger logger = Logger.getLogger(HelpMessageService.class.getName());
    
    @Inject
    DataSource dataSource;
    
    public List<HelpMessage> getUserMessages(Long userId) {
        try {
            return findByUserId(userId);
        } catch (Exception e) {
            logger.severe("Error getting user messages: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    public boolean saveMessage(Long userId, String subject, String message) {
        try {
            save(userId, subject, message);
            return true;
        } catch (Exception e) {
            logger.severe("Error saving help message: " + e.getMessage());
            return false;
        }
    }
    
    private void save(Long userId, String subject, String message) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO transactions (user_id, type, category, amount, net_amount, description, help_subject, help_message, help_status, created_at) VALUES (?, 'ADMIN', 'HELP_DESK', 0, 0, ?, ?, ?, 'OPEN', ?)")) {
            stmt.setLong(1, userId);
            stmt.setString(2, "Help request: " + subject);
            stmt.setString(3, subject);
            stmt.setString(4, message);
            stmt.setTimestamp(5, java.sql.Timestamp.valueOf(LocalDateTime.now()));
            stmt.executeUpdate();
        }
    }
    
    private List<HelpMessage> findByUserId(Long userId) throws SQLException {
        List<HelpMessage> messages = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM transactions WHERE user_id = ? AND help_status IS NOT NULL ORDER BY created_at DESC")) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapTransactionToHelpMessage(rs));
                }
            }
        }
        return messages;
    }
    
    private HelpMessage mapTransactionToHelpMessage(ResultSet rs) throws SQLException {
        HelpMessage msg = new HelpMessage();
        msg.id = rs.getLong("id");
        msg.userId = rs.getLong("user_id");
        msg.subject = rs.getString("help_subject");
        msg.message = rs.getString("help_message");
        msg.adminReply = rs.getString("admin_response");
        msg.status = rs.getString("help_status");
        msg.createdAt = rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null;
        msg.repliedAt = rs.getTimestamp("response_date") != null ? rs.getTimestamp("response_date").toLocalDateTime() : null;
        return msg;
    }
}