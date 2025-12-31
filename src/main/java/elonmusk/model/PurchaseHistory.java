package elonmusk.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PurchaseHistory {
    public Long id;
    public Long userId;
    public Long productId;
    public String productName;
    public String productImage;
    public BigDecimal amount;
    public String purchaseType = "INVESTMENT";
    public String status = "COMPLETED";
    public LocalDateTime createdAt = LocalDateTime.now();
    
    public PurchaseHistory() {}
    
    public PurchaseHistory(Long userId, Long productId, String productName, String productImage, BigDecimal amount) {
        this.userId = userId;
        this.productId = productId;
        this.productName = productName;
        this.productImage = productImage;
        this.amount = amount;
    }
}