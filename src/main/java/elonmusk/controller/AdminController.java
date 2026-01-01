package elonmusk.controller;

import elonmusk.model.User;
import elonmusk.service.UserService;
import elonmusk.service.AdminValidationService;
import elonmusk.validation.InputValidator;
import elonmusk.exception.ValidationException;
import elonmusk.exception.ServiceException;
import elonmusk.filter.SecurityUtil;
import elonmusk.config.AdminConfig;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.NewCookie;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.math.BigDecimal;
import javax.sql.DataSource;

@Path("/million")
@Produces(MediaType.TEXT_HTML)
@Blocking
public class AdminController {
    
    private static final Logger logger = Logger.getLogger(AdminController.class.getName());
    
    @Inject AdminConfig adminConfig;
    @Inject Template adminLogin;
    @Inject Template adminDashboard;
    @Inject Template adminUsers;
    @Inject Template adminUserEdit;
    @Inject Template adminDeposits;
    @Inject Template adminWithdrawals;
    @Inject Template adminProducts;
    @Inject Template adminReferrals;
    @Inject Template adminPasswordResets;
    @Inject UserService userService;
    @Inject AdminValidationService adminValidationService;
    @Inject elonmusk.service.DepositService depositService;
    @Inject elonmusk.service.WithdrawalService withdrawalService;
    @Inject elonmusk.service.InvestmentService investmentService;
    @Inject elonmusk.service.SecurityService securityService;
    @Inject elonmusk.service.ReferralBonusService referralBonusService;
    @Inject elonmusk.service.GiftCodeService giftCodeService;
    @Inject DataSource dataSource;
    
    private boolean isAdminAuthenticated(String adminId) {
        return adminId != null && "admin_authenticated".equals(adminId);
    }
    
    @GET
    @Path("/deposits")
    public TemplateInstance adminDepositsPage(@CookieParam("admin_id") String adminId,
                                            @QueryParam("success") String success,
                                            @QueryParam("error") String error) {
        if (!isAdminAuthenticated(adminId)) {
            throw new WebApplicationException(Response.seeOther(java.net.URI.create("/million/login")).build());
        }
        
        try {
            List<elonmusk.model.DepositRequest> deposits = depositService.getAllDeposits();
            return adminDeposits.data("deposits", deposits)
                              .data("success", SecurityUtil.sanitizeInput(success))
                              .data("error", SecurityUtil.sanitizeInput(error));
        } catch (Exception e) {
            logger.severe("Error loading deposits: " + e.getMessage());
            return adminDeposits.data("deposits", java.util.Collections.emptyList())
                              .data("error", "Failed to load deposits");
        }
    }
    
    @POST
    @Path("/deposit/approve")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response approveDeposit(@CookieParam("admin_id") String adminId,
                                 @FormParam("depositId") String depositIdStr,
                                 @FormParam("amount") String amountStr) {
        try {
            if (!isAdminAuthenticated(adminId)) {
                throw new WebApplicationException(Response.seeOther(java.net.URI.create("/million/login")).build());
            }
            
            if (depositIdStr == null || depositIdStr.trim().isEmpty()) {
                return Response.seeOther(java.net.URI.create("/million/deposits?error=Deposit+ID+required")).build();
            }
            
            if (amountStr == null || amountStr.trim().isEmpty()) {
                return Response.seeOther(java.net.URI.create("/million/deposits?error=Amount+required")).build();
            }
            
            Long depositId = Long.parseLong(depositIdStr.trim());
            BigDecimal amount = new BigDecimal(amountStr.trim());
            
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return Response.seeOther(java.net.URI.create("/million/deposits?error=Invalid+amount")).build();
            }
            
            depositService.approveDepositWithAmount(depositId, amount);
            logger.info("ADMIN_AUDIT: APPROVE_DEPOSIT - Deposit ID: " + depositId + ", Amount: " + amount + " USD");
            
            return Response.seeOther(java.net.URI.create("/million/deposits?success=Deposit+approved+successfully")).build();
            
        } catch (NumberFormatException e) {
            return Response.seeOther(java.net.URI.create("/million/deposits?error=Invalid+number+format")).build();
        } catch (Exception e) {
            logger.severe("Deposit approval error: " + e.getMessage());
            return Response.seeOther(java.net.URI.create("/million/deposits?error=Approval+failed")).build();
        }
    }
    
    @POST
    @Path("/deposit/reject")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response rejectDeposit(@CookieParam("admin_id") String adminId,
                                @FormParam("depositId") String depositIdStr,
                                @FormParam("reason") String reason) {
        try {
            if (!isAdminAuthenticated(adminId)) {
                throw new WebApplicationException(Response.seeOther(java.net.URI.create("/million/login")).build());
            }
            
            if (depositIdStr == null || depositIdStr.trim().isEmpty()) {
                return Response.seeOther(java.net.URI.create("/million/deposits?error=Deposit+ID+required")).build();
            }
            
            Long depositId = Long.parseLong(depositIdStr.trim());
            String sanitizedReason = reason != null ? SecurityUtil.sanitizeInput(reason.trim()) : "Rejected by admin";
            
            depositService.rejectDeposit(depositId, sanitizedReason);
            logger.info("ADMIN_AUDIT: REJECT_DEPOSIT - Deposit ID: " + depositId + ", Reason: " + sanitizedReason);
            
            return Response.seeOther(java.net.URI.create("/million/deposits?success=Deposit+rejected+successfully")).build();
            
        } catch (NumberFormatException e) {
            return Response.seeOther(java.net.URI.create("/million/deposits?error=Invalid+deposit+ID")).build();
        } catch (Exception e) {
            logger.severe("Deposit rejection error: " + e.getMessage());
            return Response.seeOther(java.net.URI.create("/million/deposits?error=Rejection+failed")).build();
        }
    }

    @GET
    @Path("/login")
    public TemplateInstance adminLoginPage(@QueryParam("error") String error) {
        return adminLogin.data("error", SecurityUtil.sanitizeInput(error));
    }
    
    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response processAdminLogin(@FormParam("username") String username,
                                    @FormParam("password") String password) {
        try {
            // Validate input parameters
            if (username == null || username.trim().isEmpty()) {
                return Response.seeOther(java.net.URI.create("/million/login?error=Username+is+required")).build();
            }
            
            if (password == null || password.trim().isEmpty()) {
                return Response.seeOther(java.net.URI.create("/million/login?error=Password+is+required")).build();
            }
            
            // Sanitize inputs
            username = SecurityUtil.sanitizeInput(username.trim());
            password = SecurityUtil.sanitizeInput(password.trim());
            
            // Validate credentials using service
            adminValidationService.validateAdminCredentials(username, password);
            
            // Check against credentials from environment variables
            if (!adminConfig.validateCredentials(username, password)) {
                adminValidationService.logAdminOperation("LOGIN_FAILED", "Username: " + username + ", IP: " + "unknown");
                logger.warning("Failed admin login attempt - Username: " + username);
                return Response.seeOther(java.net.URI.create("/million/login?error=Invalid+username+or+password")).build();
            }
            
            // Create secure admin session cookie
            NewCookie adminCookie = new NewCookie.Builder("admin_id")
                    .value("admin_authenticated")
                    .path("/")
                    .maxAge(3600) // 1 hour
                    .httpOnly(true)
                    .secure(false) // Set to true in production with HTTPS
                    .build();
            
            adminValidationService.logAdminOperation("LOGIN_SUCCESS", "Username: " + username);
            logger.info("Successful admin login - Username: " + username);
            
            return Response.seeOther(java.net.URI.create("/million/dashboard"))
                    .cookie(adminCookie)
                    .build();
                    
        } catch (ValidationException e) {
            logger.warning("Admin login validation failed: " + e.getMessage());
            adminValidationService.logAdminOperation("LOGIN_VALIDATION_FAILED", e.getMessage());
            return Response.seeOther(java.net.URI.create("/million/login?error=" + e.getMessage().replace(" ", "+"))).build();
        } catch (Exception e) {
            logger.severe("Admin login error: " + e.getMessage());
            adminValidationService.logAdminOperation("LOGIN_ERROR", "Error: " + e.getMessage());
            return Response.seeOther(java.net.URI.create("/million/login?error=Login+failed.+Please+try+again")).build();
        }
    }
    
    @GET
    @Path("/dashboard")
    public TemplateInstance adminDashboardPage(@CookieParam("admin_id") String adminId,
                                             @QueryParam("success") String success,
                                             @QueryParam("error") String error) {
        if (!isAdminAuthenticated(adminId)) {
            throw new WebApplicationException(Response.seeOther(java.net.URI.create("/million/login")).build());
        }
        return adminDashboard.data("success", SecurityUtil.sanitizeInput(success))
                           .data("error", SecurityUtil.sanitizeInput(error));
    }
    
    @GET
    @Path("/users")
    public TemplateInstance adminUsersPage(@CookieParam("admin_id") String adminId,
                                         @QueryParam("success") String success,
                                         @QueryParam("error") String error) {
        if (!isAdminAuthenticated(adminId)) {
            throw new WebApplicationException(Response.seeOther(java.net.URI.create("/million/login")).build());
        }
        
        try {
            List<User> users = userService.findAll();
            return adminUsers.data("users", users)
                           .data("success", SecurityUtil.sanitizeInput(success))
                           .data("error", SecurityUtil.sanitizeInput(error));
        } catch (Exception e) {
            logger.severe("Error loading users: " + e.getMessage());
            return adminUsers.data("users", java.util.Collections.emptyList())
                           .data("success", SecurityUtil.sanitizeInput(success))
                           .data("error", "Failed to retrieve users: " + e.getMessage());
        }
    }
    
    @GET
    @Path("/withdrawals")
    public TemplateInstance adminWithdrawalsPage(@CookieParam("admin_id") String adminId,
                                               @QueryParam("success") String success,
                                               @QueryParam("error") String error) {
        if (!isAdminAuthenticated(adminId)) {
            throw new WebApplicationException(Response.seeOther(java.net.URI.create("/million/login")).build());
        }
        
        try {
            List<elonmusk.model.WithdrawalRequest> withdrawals = withdrawalService.getAllRequests();
            return adminWithdrawals.data("withdrawals", withdrawals)
                                  .data("success", SecurityUtil.sanitizeInput(success))
                                  .data("error", SecurityUtil.sanitizeInput(error));
        } catch (Exception e) {
            logger.severe("Error loading withdrawals: " + e.getMessage());
            return adminWithdrawals.data("withdrawals", java.util.Collections.emptyList())
                                  .data("error", "Failed to load withdrawals");
        }
    }
    
    @GET
    @Path("/products")
    public TemplateInstance adminProductsPage(@CookieParam("admin_id") String adminId,
                                            @QueryParam("success") String success,
                                            @QueryParam("error") String error) {
        if (!isAdminAuthenticated(adminId)) {
            throw new WebApplicationException(Response.seeOther(java.net.URI.create("/million/login")).build());
        }
        
        try {
            List<elonmusk.model.InvestmentProduct> products = investmentService.getAllProducts();
            return adminProducts.data("products", products)
                              .data("success", SecurityUtil.sanitizeInput(success))
                              .data("error", SecurityUtil.sanitizeInput(error));
        } catch (Exception e) {
            logger.severe("Error loading products: " + e.getMessage());
            return adminProducts.data("products", java.util.Collections.emptyList())
                              .data("error", "Failed to load products");
        }
    }
    
    @POST
    @Path("/user/update")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response updateUser(@CookieParam("admin_id") String adminId,
                             @FormParam("userId") String userIdStr,
                             @FormParam("name") String name,
                             @FormParam("email") String email,
                             @FormParam("balance") String balanceStr,
                             @FormParam("password") String password) {
        try {
            if (!isAdminAuthenticated(adminId)) {
                throw new WebApplicationException(Response.seeOther(java.net.URI.create("/million/login")).build());
            }
            
            Long userId = Long.parseLong(userIdStr.trim());
            adminValidationService.validateUserUpdate(userId, name, email, balanceStr);
            
            User user = userService.findById(userId);
            if (user == null) {
                return Response.seeOther(java.net.URI.create("/million/users?error=User+not+found")).build();
            }
            
            if (name != null && !name.trim().isEmpty()) {
                userService.updateUserName(userId, name.trim());
            }
            if (email != null && !email.trim().isEmpty()) {
                userService.updateUserEmail(userId, email.trim());
            }
            if (balanceStr != null && !balanceStr.trim().isEmpty()) {
                userService.updateWalletBalance(userId, new BigDecimal(balanceStr.trim()));
            }
            
            // Update password if provided
            if (password != null && !password.trim().isEmpty()) {
                userService.updatePassword(userId, password.trim());
            }
            logger.info("ADMIN_AUDIT: UPDATE_USER - User ID: " + userId);
            
            return Response.seeOther(java.net.URI.create("/million/users?success=User+updated+successfully")).build();
            
        } catch (Exception e) {
            logger.severe("User update error: " + e.getMessage());
            return Response.seeOther(java.net.URI.create("/million/users?error=Update+failed")).build();
        }
    }
    
    @POST
    @Path("/withdrawal/approve")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response approveWithdrawal(@CookieParam("admin_id") String adminId,
                                    @FormParam("withdrawalId") String withdrawalIdStr) {
        try {
            if (!isAdminAuthenticated(adminId)) {
                throw new WebApplicationException(Response.seeOther(java.net.URI.create("/million/login")).build());
            }
            
            Long withdrawalId = Long.parseLong(withdrawalIdStr.trim());
            withdrawalService.approveWithdrawal(withdrawalId);
            logger.info("ADMIN_AUDIT: APPROVE_WITHDRAWAL - Withdrawal ID: " + withdrawalId);
            
            return Response.seeOther(java.net.URI.create("/million/withdrawals?success=Withdrawal+approved+successfully")).build();
            
        } catch (Exception e) {
            logger.severe("Withdrawal approval error: " + e.getMessage());
            return Response.seeOther(java.net.URI.create("/million/withdrawals?error=Approval+failed")).build();
        }
    }
    
    @POST
    @Path("/withdrawal/reject")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response rejectWithdrawal(@CookieParam("admin_id") String adminId,
                                   @FormParam("withdrawalId") String withdrawalIdStr,
                                   @FormParam("reason") String reason) {
        try {
            if (!isAdminAuthenticated(adminId)) {
                throw new WebApplicationException(Response.seeOther(java.net.URI.create("/million/login")).build());
            }
            
            Long withdrawalId = Long.parseLong(withdrawalIdStr.trim());
            String sanitizedReason = reason != null ? SecurityUtil.sanitizeInput(reason.trim()) : "Rejected by admin";
            
            withdrawalService.rejectWithdrawal(withdrawalId, sanitizedReason);
            logger.info("ADMIN_AUDIT: REJECT_WITHDRAWAL - Withdrawal ID: " + withdrawalId + ", Reason: " + sanitizedReason);
            
            return Response.seeOther(java.net.URI.create("/million/withdrawals?success=Withdrawal+rejected+successfully")).build();
            
        } catch (Exception e) {
            logger.severe("Withdrawal rejection error: " + e.getMessage());
            return Response.seeOther(java.net.URI.create("/million/withdrawals?error=Rejection+failed")).build();
        }
    }
    
    @GET
    @Path("/referrals")
    public TemplateInstance adminReferralsPage(@CookieParam("admin_id") String adminId,
                                             @QueryParam("success") String success,
                                             @QueryParam("error") String error) {
        if (!isAdminAuthenticated(adminId)) {
            throw new WebApplicationException(Response.seeOther(java.net.URI.create("/million/login")).build());
        }
        
        try {
            // Get referral statistics
            List<User> topReferrers = userService.findAll().stream()
                .filter(u -> u.totalReferrals != null && u.totalReferrals > 0)
                .sorted((a, b) -> Integer.compare(b.totalReferrals, a.totalReferrals))
                .limit(10)
                .collect(java.util.stream.Collectors.toList());
            
            int totalReferrals = topReferrers.stream().mapToInt(u -> u.totalReferrals != null ? u.totalReferrals : 0).sum();
            int activeReferrers = topReferrers.size();
            java.math.BigDecimal totalBonusPaid = topReferrers.stream()
                .map(u -> u.referralEarnings != null ? u.referralEarnings : java.math.BigDecimal.ZERO)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            
            return adminReferrals.data("topReferrers", topReferrers)
                                .data("totalReferrals", totalReferrals)
                                .data("activeReferrers", activeReferrers)
                                .data("totalBonusPaid", totalBonusPaid)
                                .data("pendingBonuses", 0)
                                .data("recentActivity", java.util.Collections.emptyList())
                                .data("success", SecurityUtil.sanitizeInput(success))
                                .data("error", SecurityUtil.sanitizeInput(error));
        } catch (Exception e) {
            logger.severe("Error loading referrals: " + e.getMessage());
            return adminReferrals.data("topReferrers", java.util.Collections.emptyList())
                                .data("totalReferrals", 0)
                                .data("activeReferrers", 0)
                                .data("totalBonusPaid", java.math.BigDecimal.ZERO)
                                .data("pendingBonuses", 0)
                                .data("recentActivity", java.util.Collections.emptyList())
                                .data("error", "Failed to load referral data");
        }
    }
    
    @GET
    @Path("/user/edit")
    public TemplateInstance adminUserEditPage(@CookieParam("admin_id") String adminId,
                                            @QueryParam("id") String userIdStr,
                                            @QueryParam("error") String error) {
        if (!isAdminAuthenticated(adminId)) {
            throw new WebApplicationException(Response.seeOther(java.net.URI.create("/million/login")).build());
        }
        
        try {
            Long userId = Long.parseLong(userIdStr);
            User user = userService.findById(userId);
            if (user == null) {
                return adminUsers.data("users", java.util.Collections.emptyList())
                               .data("error", "User not found");
            }
            return adminUserEdit.data("user", user)
                              .data("error", SecurityUtil.sanitizeInput(error));
        } catch (Exception e) {
            logger.severe("Error loading user for edit: " + e.getMessage());
            return adminUsers.data("users", java.util.Collections.emptyList())
                           .data("error", "Failed to load user");
        }
    }
    
    @POST
    @Path("/add-product")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response addProduct(@CookieParam("admin_id") String adminId,
                             @FormParam("name") String name,
                             @FormParam("description") String description,
                             @FormParam("price") String priceStr,
                             @FormParam("dailyReturn") String dailyReturnStr,
                             @FormParam("duration") String durationStr,
                             @FormParam("imageUrl") String imageUrl,
                             @FormParam("active") String active) {
        try {
            if (!isAdminAuthenticated(adminId)) {
                throw new WebApplicationException(Response.seeOther(java.net.URI.create("/million/login")).build());
            }
            
            // Validate inputs
            if (name == null || name.trim().isEmpty()) {
                return Response.seeOther(java.net.URI.create("/million/products?error=Product+name+required")).build();
            }
            
            BigDecimal price = new BigDecimal(priceStr.trim());
            BigDecimal dailyReturn = new BigDecimal(dailyReturnStr.trim());
            Integer duration = Integer.parseInt(durationStr.trim());
            
            // Create product using investment service
            elonmusk.model.InvestmentProduct product = new elonmusk.model.InvestmentProduct();
            product.name = name.trim();
            product.description = description != null ? description.trim() : "";
            product.price = price;
            product.dailyReturnRate = dailyReturn.divide(new BigDecimal("100")); // Convert percentage to decimal
            product.durationDays = duration;
            product.imageUrl = imageUrl != null && !imageUrl.trim().isEmpty() ? imageUrl.trim() : "/product/default.jpg";
            product.isActive = "on".equals(active);
            product.category = "STANDARD";
            product.riskLevel = "MEDIUM";
            
            // Simple insert without using createProduct method
            String sql = "INSERT INTO investment_products (name, description, price, daily_return_rate, duration_days, risk_level, category, image_url, is_active, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)";
            try (java.sql.Connection conn = dataSource.getConnection();
                 java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, product.name);
                stmt.setString(2, product.description);
                stmt.setBigDecimal(3, product.price);
                stmt.setBigDecimal(4, product.dailyReturnRate);
                stmt.setInt(5, product.durationDays);
                stmt.setString(6, product.riskLevel);
                stmt.setString(7, product.category);
                stmt.setString(8, product.imageUrl);
                stmt.setBoolean(9, product.isActive);
                stmt.setTimestamp(10, java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()));
                stmt.setTimestamp(11, java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()));
                stmt.executeUpdate();
            }
            logger.info("ADMIN_AUDIT: ADD_PRODUCT - Name: " + name + ", Price: " + price);
            
            return Response.seeOther(java.net.URI.create("/million/products?success=Product+added+successfully")).build();
            
        } catch (Exception e) {
            logger.severe("Add product error: " + e.getMessage());
            return Response.seeOther(java.net.URI.create("/million/products?error=Failed+to+add+product")).build();
        }
    }
    
    @POST
    @Path("/update-product")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response updateProduct(@CookieParam("admin_id") String adminId,
                                @FormParam("id") String idStr,
                                @FormParam("name") String name,
                                @FormParam("description") String description,
                                @FormParam("price") String priceStr,
                                @FormParam("dailyReturn") String dailyReturnStr,
                                @FormParam("duration") String durationStr,
                                @FormParam("imageUrl") String imageUrl,
                                @FormParam("active") String active) {
        try {
            if (!isAdminAuthenticated(adminId)) {
                throw new WebApplicationException(Response.seeOther(java.net.URI.create("/million/login")).build());
            }
            
            Long productId = Long.parseLong(idStr.trim());
            BigDecimal price = new BigDecimal(priceStr.trim());
            BigDecimal dailyReturn = new BigDecimal(dailyReturnStr.trim());
            Integer duration = Integer.parseInt(durationStr.trim());
            
            // Direct SQL update
            String sql = "UPDATE investment_products SET name = ?, description = ?, price = ?, daily_return_rate = ?, duration_days = ?, image_url = ?, is_active = ?, updated_at = ? WHERE id = ?";
            try (java.sql.Connection conn = dataSource.getConnection();
                 java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, name.trim());
                stmt.setString(2, description.trim());
                stmt.setBigDecimal(3, price);
                stmt.setBigDecimal(4, dailyReturn.divide(new BigDecimal("100")));
                stmt.setInt(5, duration);
                stmt.setString(6, imageUrl != null ? imageUrl.trim() : "/product/default.jpg");
                stmt.setBoolean(7, "on".equals(active));
                stmt.setTimestamp(8, java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()));
                stmt.setLong(9, productId);
                stmt.executeUpdate();
            }
            
            logger.info("ADMIN_AUDIT: UPDATE_PRODUCT - ID: " + productId);
            
            return Response.seeOther(java.net.URI.create("/million/products?success=Product+updated+successfully")).build();
            
        } catch (Exception e) {
            logger.severe("Update product error: " + e.getMessage());
            return Response.seeOther(java.net.URI.create("/million/products?error=Failed+to+update+product")).build();
        }
    }
    
    @POST
    @Path("/delete-product")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response deleteProduct(@CookieParam("admin_id") String adminId,
                                @FormParam("id") String idStr) {
        try {
            if (!isAdminAuthenticated(adminId)) {
                throw new WebApplicationException(Response.seeOther(java.net.URI.create("/million/login")).build());
            }
            
            Long productId = Long.parseLong(idStr.trim());
            
            // Direct SQL delete
            String sql = "DELETE FROM investment_products WHERE id = ?";
            try (java.sql.Connection conn = dataSource.getConnection();
                 java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, productId);
                stmt.executeUpdate();
            }
            
            logger.info("ADMIN_AUDIT: DELETE_PRODUCT - ID: " + productId);
            
            return Response.seeOther(java.net.URI.create("/million/products?success=Product+deleted+successfully")).build();
            
        } catch (Exception e) {
            logger.severe("Delete product error: " + e.getMessage());
            return Response.seeOther(java.net.URI.create("/million/products?error=Failed+to+delete+product")).build();
        }
    }
    
    @POST
    @Path("/user/delete")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response deleteUser(@CookieParam("admin_id") String adminId,
                             @FormParam("userId") String userIdStr) {
        try {
            if (!isAdminAuthenticated(adminId)) {
                throw new WebApplicationException(Response.seeOther(java.net.URI.create("/million/login")).build());
            }
            
            if (userIdStr == null || userIdStr.trim().isEmpty()) {
                return Response.seeOther(java.net.URI.create("/million/users?error=User+ID+required")).build();
            }
            
            Long userId = Long.parseLong(userIdStr.trim());
            boolean deleted = userService.deleteUser(userId);
            
            if (deleted) {
                logger.info("ADMIN_AUDIT: DELETE_USER - User ID: " + userId);
                return Response.seeOther(java.net.URI.create("/million/users?success=User+deleted+successfully")).build();
            } else {
                return Response.seeOther(java.net.URI.create("/million/users?error=User+not+found")).build();
            }
            
        } catch (NumberFormatException e) {
            return Response.seeOther(java.net.URI.create("/million/users?error=Invalid+user+ID")).build();
        } catch (Exception e) {
            logger.severe("User deletion error: " + e.getMessage());
            return Response.seeOther(java.net.URI.create("/million/users?error=Delete+failed")).build();
        }
    }
    
    @POST
    @Path("/gift-code/create")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response createGiftCode(@CookieParam("admin_id") String adminId,
                                 @FormParam("code") String code,
                                 @FormParam("amount") String amountStr,
                                 @FormParam("duration") String durationStr) {
        try {
            if (!isAdminAuthenticated(adminId)) {
                throw new WebApplicationException(Response.seeOther(java.net.URI.create("/million/login")).build());
            }
            
            if (amountStr == null || amountStr.trim().isEmpty()) {
                return Response.seeOther(java.net.URI.create("/million/dashboard?error=Amount+required")).build();
            }
            
            if (durationStr == null || durationStr.trim().isEmpty()) {
                return Response.seeOther(java.net.URI.create("/million/dashboard?error=Duration+required")).build();
            }
            
            BigDecimal amount = new BigDecimal(amountStr.trim());
            int durationMinutes = Integer.parseInt(durationStr.trim());
            
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return Response.seeOther(java.net.URI.create("/million/dashboard?error=Invalid+amount")).build();
            }
            
            if (durationMinutes <= 0 || durationMinutes > 43200) { // Max 30 days
                return Response.seeOther(java.net.URI.create("/million/dashboard?error=Duration+must+be+1-43200+minutes")).build();
            }
            
            // Generate code if not provided or if "RANDOM" is specified
            String finalCode = null;
            if (code == null || code.trim().isEmpty() || "RANDOM".equalsIgnoreCase(code.trim())) {
                // Generate unique random code (retry up to 10 times if duplicate)
                int attempts = 0;
                int maxAttempts = 10;
                boolean codeCreated = false;
                
                while (attempts < maxAttempts && !codeCreated) {
                    finalCode = elonmusk.util.TokenGenerator.generateGiftCode();
                    try {
                        giftCodeService.createGiftCode(finalCode, amount, durationMinutes);
                        codeCreated = true;
                        logger.info("Generated random gift code: " + finalCode);
                    } catch (ValidationException e) {
                        if (e.getMessage().contains("already exists")) {
                            attempts++;
                            if (attempts >= maxAttempts) {
                                return Response.seeOther(java.net.URI.create("/million/dashboard?error=Failed+to+generate+unique+code.+Please+try+again")).build();
                            }
                            // Try again with new code
                            continue;
                        }
                        throw e;
                    }
                }
            } else {
                finalCode = code.trim().toUpperCase();
                // Validate the provided code format
                if (!elonmusk.util.TokenGenerator.isValidGiftCodeFormat(finalCode)) {
                    return Response.seeOther(java.net.URI.create("/million/dashboard?error=Invalid+code+format.+Must+be+8+characters+A-Z+and+0-9+only")).build();
                }
                // Create with validation
                giftCodeService.createGiftCode(finalCode, amount, durationMinutes);
            }
            logger.info("ADMIN_AUDIT: CREATE_GIFT_CODE - Code: " + finalCode + ", Amount: $" + amount + ", Duration: " + durationMinutes + " minutes");
            
            return Response.seeOther(java.net.URI.create("/million/dashboard?success=Gift+code+created+successfully:+" + finalCode)).build();
            
        } catch (ValidationException e) {
            String errorMsg = e.getMessage().replace(" ", "+");
            return Response.seeOther(java.net.URI.create("/million/dashboard?error=" + errorMsg)).build();
        } catch (NumberFormatException e) {
            return Response.seeOther(java.net.URI.create("/million/dashboard?error=Invalid+number+format")).build();
        } catch (Exception e) {
            logger.severe("Gift code creation error: " + e.getMessage());
            return Response.seeOther(java.net.URI.create("/million/dashboard?error=Failed+to+create+gift+code")).build();
        }
    }
    
    @GET
    @Path("/logout")
    public Response adminLogout() {
        NewCookie clearCookie = new NewCookie.Builder("admin_id")
                .value("")
                .path("/")
                .maxAge(0)
                .build();
                
        return Response.seeOther(java.net.URI.create("/million/login"))
                .cookie(clearCookie)
                .build();
    }
    
    @GET
    @Path("/test-db")
    @Produces(MediaType.APPLICATION_JSON)
    public Response testDatabaseConnection() {
        try {
            // Test basic database connectivity
            java.sql.Connection connection = dataSource.getConnection();
            java.sql.Statement stmt = connection.createStatement();
            java.sql.ResultSet rs = stmt.executeQuery("SELECT 1 as test");
            
            if (rs.next() && rs.getInt("test") == 1) {
                connection.close();
                return Response.ok("{\"status\":\"success\",\"message\":\"Database connection works\"}").build();
            } else {
                connection.close();
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity("{\"status\":\"error\",\"message\":\"Database query failed\"}")
                        .build();
            }
        } catch (Exception e) {
            logger.severe("Database test failed: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"status\":\"error\",\"message\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}")
                    .build();
        }
    }
    
    @GET
    @Path("/test-simple")
    @Produces(MediaType.TEXT_PLAIN)
    public String testSimple() {
        return "Application is running! Time: " + new java.util.Date();
    }
    
}