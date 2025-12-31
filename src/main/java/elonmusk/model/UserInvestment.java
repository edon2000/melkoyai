package elonmusk.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class UserInvestment {
    public Long id;
    public Long userId;
    public String productName;
    public BigDecimal investedAmount;
    public BigDecimal dailyReturn;
    public BigDecimal totalReturn;
    public String status;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    
    public UserInvestment() {
        this.totalReturn = BigDecimal.ZERO;
        this.status = "ACTIVE";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}