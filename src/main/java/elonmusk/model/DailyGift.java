package elonmusk.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class DailyGift {
    public Long id;
    public Long userId;
    public BigDecimal giftAmount;
    public LocalDate giftDate;
    public Boolean isCollected = false;
    public LocalDateTime collectedAt;
    public String source = "DAILY_INCOME";
    public LocalDateTime createdAt = LocalDateTime.now();
    
    public DailyGift() {}
    
    public DailyGift(Long userId, BigDecimal giftAmount, LocalDate giftDate) {
        this.userId = userId;
        this.giftAmount = giftAmount;
        this.giftDate = giftDate;
    }
}