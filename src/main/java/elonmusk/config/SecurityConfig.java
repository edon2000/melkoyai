package elonmusk.config;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.math.BigDecimal;

@ApplicationScoped
public class SecurityConfig {
    
    public static final List<String> ALLOWED_PAYMENT_METHODS = Arrays.asList("cbe", "telebirr");
    public static final List<String> ALLOWED_STATUSES = Arrays.asList("PENDING", "APPROVED", "REJECTED");
    public static final int MAX_LOGIN_ATTEMPTS = 5;
    public static final int SESSION_TIMEOUT_MINUTES = 30;
    public static final int MAX_DAILY_QUESTIONS = 3;
    public static final int MAX_TRANSACTION_ID_LENGTH = 50;
    public static final int MIN_TRANSACTION_ID_LENGTH = 6;
    public static final BigDecimal MIN_DEPOSIT_AMOUNT = new BigDecimal("3.50");
    public static final BigDecimal MAX_DEPOSIT_AMOUNT = new BigDecimal("75000.00");
    public static final BigDecimal MIN_WITHDRAWAL_AMOUNT = new BigDecimal("5.00");
    public static final BigDecimal MAX_WITHDRAWAL_AMOUNT = new BigDecimal("20000.00");
    
    // Security headers
    public static final String CONTENT_SECURITY_POLICY = 
        "default-src 'self'; " +
        "script-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com https://cdn.jsdelivr.net; " +
        "style-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com https://cdn.jsdelivr.net; " +
        "img-src 'self' data: https:; " +
        "font-src 'self' https://cdnjs.cloudflare.com https://cdn.jsdelivr.net; " +
        "frame-ancestors 'none'; " +
        "base-uri 'self'; " +
        "form-action 'self';";
    
    public static final Set<String> BLOCKED_USER_AGENTS = new HashSet<>(Arrays.asList(
        "sqlmap", "nikto", "nmap", "masscan", "nessus", "openvas", "burp", "zap"
    ));
    
    public static boolean isValidPaymentMethod(String method) {
        return method != null && ALLOWED_PAYMENT_METHODS.contains(method.toLowerCase());
    }
    
    public static boolean isValidStatus(String status) {
        return status != null && ALLOWED_STATUSES.contains(status.toUpperCase());
    }
    
    public static boolean isValidAmount(BigDecimal amount, BigDecimal min, BigDecimal max) {
        if (amount == null) return false;
        return amount.compareTo(min) >= 0 && amount.compareTo(max) <= 0;
    }
    
    public static boolean isBlockedUserAgent(String userAgent) {
        if (userAgent == null) return false;
        String lowerUserAgent = userAgent.toLowerCase();
        return BLOCKED_USER_AGENTS.stream().anyMatch(lowerUserAgent::contains);
    }
    
    public static String[] getSecurityHeaders() {
        return new String[]{
            "X-Frame-Options", "DENY",
            "X-Content-Type-Options", "nosniff",
            "X-XSS-Protection", "1; mode=block",
            "Referrer-Policy", "strict-origin-when-cross-origin",
            "Permissions-Policy", "geolocation=(), microphone=(), camera=()",
            "Strict-Transport-Security", "max-age=31536000; includeSubDomains"
        };
    }
}