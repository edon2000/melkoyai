package elonmusk.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import io.quarkus.runtime.ShutdownEvent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@ApplicationScoped
public class AppConfig {
    
    private static final Logger LOGGER = Logger.getLogger(AppConfig.class.getName());
    private ScheduledExecutorService executorService;
    
    // Security constants
    public static final List<String> ALLOWED_PAYMENT_METHODS = Arrays.asList("cbe", "telebirr");
    public static final List<String> ALLOWED_STATUSES = Arrays.asList("PENDING", "APPROVED", "REJECTED");
    public static final int MAX_LOGIN_ATTEMPTS = 5;
    public static final int SESSION_TIMEOUT_MINUTES = 30;
    public static final int MAX_DAILY_QUESTIONS = 3;
    public static final BigDecimal MIN_DEPOSIT_AMOUNT = new BigDecimal("10.00");
    public static final BigDecimal MAX_DEPOSIT_AMOUNT = new BigDecimal("75000.00");
    public static final BigDecimal MIN_WITHDRAWAL_AMOUNT = new BigDecimal("2.00");
    public static final BigDecimal MAX_WITHDRAWAL_AMOUNT = new BigDecimal("20000.00");
    
    public static final Set<String> BLOCKED_USER_AGENTS = new HashSet<>(Arrays.asList(
        "sqlmap", "nikto", "nmap", "masscan", "nessus", "openvas", "burp", "zap"
    ));
    
    @Produces
    @Singleton
    @AppExecutor
    public ScheduledExecutorService scheduledExecutorService() {
        LOGGER.info("Creating scheduled executor service");
        executorService = Executors.newScheduledThreadPool(2);
        return executorService;
    }
    
    void onShutdown(@Observes ShutdownEvent event) {
        if (executorService != null) {
            LOGGER.info("Shutting down scheduled executor service");
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
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
}