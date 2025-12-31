package elonmusk.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class DepositRequest {
    public Long id;
    public Long userId;
    public String transactionId;
    public String paymentMethod;
    public BigDecimal amount;
    public String status;
    public String adminNotes;
    public LocalDateTime createdAt;
    public LocalDateTime processedAt;
    
    // Additional fields for display
    public String userName;
    public String userPhone;
    public String userEmail;
    
    public DepositRequest() {
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }
}