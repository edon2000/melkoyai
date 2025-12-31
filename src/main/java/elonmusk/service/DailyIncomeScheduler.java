package elonmusk.service;

import elonmusk.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.scheduler.Scheduled;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

@ApplicationScoped
public class DailyIncomeScheduler {
    
    private static final Logger LOGGER = Logger.getLogger(DailyIncomeScheduler.class.getName());
    
    @Inject
    GiftService giftService;
    
    @Inject
    UserService userService;
    
    /**
     * Generate daily income for all users every day at 00:00 UTC
     * This ensures all users with active investments receive their daily returns
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void generateDailyIncomeForAllUsers() {
        LOGGER.info("Starting daily income generation for all users...");
        
        try {
            List<User> allUsers = userService.findAll();
            int successCount = 0;
            int errorCount = 0;
            
            for (User user : allUsers) {
                try {
                    giftService.generateDailyGifts(user.id);
                    successCount++;
                } catch (Exception e) {
                    errorCount++;
                    LOGGER.log(Level.WARNING, "Failed to generate daily gifts for user " + user.id, e);
                }
            }
            
            LOGGER.info("Daily income generation completed: " + successCount + " successful, " + 
                       errorCount + " failed out of " + allUsers.size() + " users");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Critical error in daily income scheduler", e);
        }
    }
    
    /**
     * Alternative: Generate daily income every 6 hours for more frequent updates
     */
    @Scheduled(cron = "0 0 */6 * * ?")
    public void generateDailyIncomeFrequent() {
        LOGGER.info("Running frequent daily income generation check...");
        generateDailyIncomeForAllUsers();
    }
}