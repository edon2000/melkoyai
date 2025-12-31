package elonmusk.dto;

import jakarta.ws.rs.FormParam;
import java.math.BigDecimal;

public class WithdrawalFormRequest {
    @FormParam("amount")
    public BigDecimal amount;
    
    @FormParam("password")
    public String password;
}