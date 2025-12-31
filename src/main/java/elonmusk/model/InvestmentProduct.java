package elonmusk.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class InvestmentProduct {
    public Long id;
    public String name;
    public BigDecimal price;
    public BigDecimal dailyReturnRate;
    public Integer durationDays;
    public String riskLevel;
    public String category;
    public String description;
    public String imageUrl;
    public Boolean isActive;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    
    public InvestmentProduct() {
        this.isActive = true;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}