package elonmusk.service;

import elonmusk.model.User;
import elonmusk.model.InvestmentProduct;
import elonmusk.model.UserInvestment;
import elonmusk.exception.ServiceException;
import elonmusk.exception.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.agroal.api.AgroalDataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.logging.Level;

@ApplicationScoped
public class InvestmentService {
    private static final Logger logger = Logger.getLogger(InvestmentService.class.getName());
    
    @Inject
    AgroalDataSource dataSource;
    
    @Inject
    UserService userService;
    
    @Inject
    ReferralBonusService referralBonusService;
    
    @Inject
    PurchaseHistoryService purchaseHistoryService;
    
    @Inject
    GiftService giftService;
    
    public List<InvestmentProduct> getAllProducts() {
        String sql = "SELECT * FROM investment_products WHERE is_active = true ORDER BY price ASC";
        List<InvestmentProduct> products = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                InvestmentProduct product = new InvestmentProduct();
                product.id = rs.getLong("id");
                product.name = rs.getString("name");
                product.price = rs.getBigDecimal("price");
                product.dailyReturnRate = rs.getBigDecimal("daily_return_rate");
                product.durationDays = rs.getInt("duration_days");
                product.riskLevel = rs.getString("risk_level");
                product.category = rs.getString("category");
                product.description = rs.getString("description");
                product.imageUrl = rs.getString("image_url");
                product.isActive = rs.getBoolean("is_active");
                products.add(product);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error getting products", e);
        }
        
        return products;
    }
    
    public InvestmentProduct getProductById(Long productId) {
        String sql = "SELECT * FROM investment_products WHERE id = ? AND is_active = true";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, productId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    InvestmentProduct product = new InvestmentProduct();
                    product.id = rs.getLong("id");
                    product.name = rs.getString("name");
                    product.price = rs.getBigDecimal("price");
                    product.dailyReturnRate = rs.getBigDecimal("daily_return_rate");
                    product.durationDays = rs.getInt("duration_days");
                    product.riskLevel = rs.getString("risk_level");
                    product.category = rs.getString("category");
                    product.description = rs.getString("description");
                    product.imageUrl = rs.getString("image_url");
                    product.isActive = rs.getBoolean("is_active");
                    return product;
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error getting product by ID", e);
        }
        
        return null;
    }
    
    public UserInvestment purchaseProduct(User user, Long productId) {
        if (user == null || user.id == null) {
            throw new ValidationException("Valid user is required");
        }
        if (productId == null || productId <= 0) {
            throw new ValidationException("Valid product ID is required");
        }
        
        InvestmentProduct product = getProductById(productId);
        if (product == null) {
            throw new ValidationException("Product not found or not available");
        }
        
        // Refresh user data to get latest wallet balance
        User refreshedUser = userService.findById(user.id);
        if (refreshedUser == null) {
            throw new ValidationException("User not found");
        }
        
        if (refreshedUser.walletBalance == null || refreshedUser.walletBalance.compareTo(product.price) < 0) {
            throw new ValidationException("Insufficient wallet balance. You need $" + product.price + " but have $" + refreshedUser.walletBalance);
        }
        
        String updateWalletSql = "UPDATE users SET wallet_balance = wallet_balance - ?, updated_at = ? WHERE id = ? AND wallet_balance >= ?";
        String insertInvestmentSql = "INSERT INTO user_investments (user_id, product_id, product_name, amount, invested_amount, daily_return, total_return, start_date, end_date, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?)";
        String insertTransactionSql = "INSERT INTO transactions (user_id, type, category, amount, net_amount, description, status, processed_by, processed_at) VALUES (?, 'INVESTMENT', 'PURCHASE', ?, ?, ?, 'COMPLETED', 'SYSTEM', ?)";
        
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Update user wallet with balance check
                try (PreparedStatement stmt = conn.prepareStatement(updateWalletSql)) {
                    stmt.setBigDecimal(1, product.price);
                    stmt.setTimestamp(2, java.sql.Timestamp.valueOf(LocalDateTime.now()));
                    stmt.setLong(3, refreshedUser.id);
                    stmt.setBigDecimal(4, product.price); // Ensure balance is still sufficient
                    
                    int updated = stmt.executeUpdate();
                    if (updated == 0) {
                        throw new ValidationException("Insufficient balance or user not found");
                    }
                }
                
                // Calculate daily return
                BigDecimal dailyReturn = product.price.multiply(product.dailyReturnRate);
                
                // Create investment
                Long investmentId;
                try (PreparedStatement stmt = conn.prepareStatement(insertInvestmentSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                    stmt.setLong(1, refreshedUser.id);
                    stmt.setLong(2, product.id);
                    stmt.setString(3, product.name);
                    stmt.setBigDecimal(4, product.price);
                    stmt.setBigDecimal(5, product.price);
                    stmt.setBigDecimal(6, dailyReturn);
                    stmt.setBigDecimal(7, BigDecimal.ZERO);
                    stmt.setDate(8, java.sql.Date.valueOf(java.time.LocalDate.now()));
                    stmt.setDate(9, java.sql.Date.valueOf(java.time.LocalDate.now().plusDays(product.durationDays)));
                    stmt.setTimestamp(10, java.sql.Timestamp.valueOf(LocalDateTime.now()));
                    
                    int inserted = stmt.executeUpdate();
                    if (inserted == 0) {
                        throw new ServiceException("Failed to create investment");
                    }
                    
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            investmentId = rs.getLong(1);
                        } else {
                            throw new ServiceException("Failed to get investment ID");
                        }
                    }
                }
                
                // Log transaction
                try (PreparedStatement stmt = conn.prepareStatement(insertTransactionSql)) {
                    stmt.setLong(1, refreshedUser.id);
                    stmt.setBigDecimal(2, product.price);
                    stmt.setBigDecimal(3, product.price);
                    stmt.setString(4, "Investment purchase: " + product.name);
                    stmt.setTimestamp(5, java.sql.Timestamp.valueOf(LocalDateTime.now()));
                    stmt.executeUpdate();
                }
                
                conn.commit();
                
                // Process referral bonus (10% of investment amount)
                try {
                    referralBonusService.processReferralBonus(refreshedUser.id, product.price);
                } catch (Exception e) {
                    logger.warning("Failed to process referral bonus: " + e.getMessage());
                }
                
                // Record purchase in history
                try {
                    purchaseHistoryService.recordPurchase(refreshedUser.id, product.id, product.name, product.imageUrl, product.price);
                } catch (Exception e) {
                    logger.warning("Failed to record purchase history: " + e.getMessage());
                }
                
                // Generate daily gift for tomorrow
                try {
                    giftService.generateDailyGifts(refreshedUser.id);
                } catch (Exception e) {
                    logger.warning("Failed to generate daily gifts: " + e.getMessage());
                }
                
                // Create return object
                UserInvestment investment = new UserInvestment();
                investment.id = investmentId;
                investment.userId = refreshedUser.id;
                investment.productName = product.name;
                investment.investedAmount = product.price;
                investment.dailyReturn = dailyReturn;
                investment.status = "ACTIVE";
                investment.createdAt = LocalDateTime.now();
                
                logger.info("Investment purchased successfully: User " + refreshedUser.id + " bought " + product.name + " for $" + product.price);
                return investment;
                
            } catch (Exception e) {
                conn.rollback();
                logger.severe("Investment purchase failed, transaction rolled back: " + e.getMessage());
                throw e;
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error purchasing product", e);
            throw new ServiceException("Failed to process investment purchase: Database error");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error purchasing product", e);
            if (e instanceof ValidationException || e instanceof ServiceException) {
                throw e;
            }
            throw new ServiceException("Failed to process investment purchase: " + e.getMessage());
        }
    }
    
    public List<UserInvestment> getUserInvestments(Long userId) {
        String sql = "SELECT * FROM user_investments WHERE user_id = ? ORDER BY created_at DESC";
        List<UserInvestment> investments = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UserInvestment investment = new UserInvestment();
                    investment.id = rs.getLong("id");
                    investment.userId = rs.getLong("user_id");
                    investment.productName = rs.getString("product_name");
                    investment.investedAmount = rs.getBigDecimal("invested_amount");
                    investment.dailyReturn = rs.getBigDecimal("daily_return");
                    investment.totalReturn = rs.getBigDecimal("total_return");
                    investment.status = rs.getString("status");
                    investment.createdAt = rs.getTimestamp("created_at").toLocalDateTime();
                    investments.add(investment);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error getting user investments: " + e.getMessage(), e);
        }
        
        return investments;
    }
}