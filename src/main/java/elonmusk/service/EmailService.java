package elonmusk.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.util.logging.Logger;
import java.util.logging.Level;

@ApplicationScoped
public class EmailService {
    private static final Logger logger = Logger.getLogger(EmailService.class.getName());
    
    public void sendInvestmentConfirmation(String email, String userName, String productName, BigDecimal amount) {
        // Email service implementation placeholder
        // In production, this would integrate with actual email service (SendGrid, AWS SES, etc.)
        
        logger.info("Investment confirmation email would be sent to: " + email + 
                   " for user: " + userName + 
                   " product: " + productName + 
                   " amount: $" + amount);
        
        // For now, just log the email content
        String subject = "Investment Confirmation - " + productName;
        String body = "Dear " + userName + ",\n\n" +
                     "Your investment of $" + amount + " in " + productName + " has been confirmed.\n\n" +
                     "Thank you for choosing our platform!\n\n" +
                     "Best regards,\n" +
                     "ElonMusk Investment Team";
        
        logger.info("Email Subject: " + subject);
        logger.info("Email Body: " + body);
    }
    
    public void sendDepositConfirmation(String email, String userName, BigDecimal amount) {
        logger.info("Deposit confirmation email would be sent to: " + email + 
                   " for user: " + userName + 
                   " amount: $" + amount);
    }
    
    public void sendWithdrawalNotification(String email, String userName, BigDecimal amount) {
        logger.info("Withdrawal notification email would be sent to: " + email + 
                   " for user: " + userName + 
                   " amount: $" + amount);
    }
}