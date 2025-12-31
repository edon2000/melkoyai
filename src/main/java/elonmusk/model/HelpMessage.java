package elonmusk.model;

import java.time.LocalDateTime;

public class HelpMessage {
    
    public Long id;
    public Long userId;
    public String subject;
    public String message;
    public String adminReply;
    public String status = "PENDING";
    public LocalDateTime createdAt = LocalDateTime.now();
    public LocalDateTime repliedAt;
}