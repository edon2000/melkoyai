package elonmusk.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class GiftCode {
    public Long id;
    public String code;
    public BigDecimal amount;
    public LocalDateTime expiresAt;
    public Integer maxUses = 1000;
    public Integer currentUses = 0;
    public Boolean isActive = true;
    public String createdBy = "ADMIN";
    public LocalDateTime createdAt = LocalDateTime.now();
    
    public GiftCode() {}
    
    public GiftCode(String code, BigDecimal amount, LocalDateTime expiresAt) {
        this.code = code;
        this.amount = amount;
        this.expiresAt = expiresAt;
    }
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
    
    public boolean canBeUsed() {
        return isActive && !isExpired() && currentUses < maxUses;
    }
}