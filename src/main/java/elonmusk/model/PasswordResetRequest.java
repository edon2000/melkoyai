package elonmusk.model;

import java.time.LocalDateTime;

public class PasswordResetRequest {
    public Long id;
    public String phoneNumber;
    public String status;
    public LocalDateTime createdAt;
    public LocalDateTime expiresAt;
    public LocalDateTime processedAt;
    public String processedBy;
    
    public PasswordResetRequest() {
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }
}