package elonmusk.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class GiftCodeRedemption {
    public Long id;
    public Long userId;
    public Long giftCodeId;
    public String code;
    public BigDecimal amount;
    public LocalDateTime redeemedAt = LocalDateTime.now();
    
    public GiftCodeRedemption() {}
    
    public GiftCodeRedemption(Long userId, Long giftCodeId, String code, BigDecimal amount) {
        this.userId = userId;
        this.giftCodeId = giftCodeId;
        this.code = code;
        this.amount = amount;
    }
}