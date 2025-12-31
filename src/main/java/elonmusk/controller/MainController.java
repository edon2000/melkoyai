package elonmusk.controller;

import elonmusk.dto.LoginFormRequest;
import elonmusk.dto.RegisterFormRequest;
import elonmusk.model.User;
import elonmusk.model.DepositRequest;
import elonmusk.service.AuthService;
import elonmusk.validation.InputValidator;
import elonmusk.exception.ValidationException;
import elonmusk.exception.ServiceException;
import elonmusk.config.SecurityConfig;
import elonmusk.filter.SecurityUtil;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.NewCookie;
import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Context;
import java.util.logging.Logger;
import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;

@Path("")
@Produces(MediaType.TEXT_HTML)
@Blocking
public class MainController {
    
    private static final Logger logger = Logger.getLogger(MainController.class.getName());
    
    @Inject Template login;
    @Inject Template signup;
    @Inject Template home;
    @Inject Template product;
    @Inject Template help;
    @Inject Template team;
    @Inject Template my;
    @Inject Template deposit;
    @Inject Template withdrawalRequest;
    @Inject Template withdrawalHistory;
    @Inject Template gift;
    @Inject Template invite;
    @Inject Template service;
    @Inject Template income;
    @Inject Template purchase;
    @Inject Template forgotPassword;
    @Inject AuthService authService;
    @Inject elonmusk.service.UserService userService;
    @Inject elonmusk.service.DepositService depositService;
    @Inject elonmusk.service.WithdrawalService withdrawalService;
    @Inject elonmusk.service.SecurityService securityService;
    @Inject elonmusk.service.InvestmentService investmentService;
    @Inject elonmusk.service.ReferralBonusService referralBonusService;
    @Inject elonmusk.service.ReferralAcceptanceService referralAcceptanceService;
    @Inject elonmusk.service.InvitationValidationService invitationValidationService;
    @Inject elonmusk.service.ReferralValidationService referralValidationService;
    @Inject elonmusk.service.GiftService giftService;
    @Inject elonmusk.service.PurchaseHistoryService purchaseHistoryService;
    @Inject elonmusk.service.GiftCodeService giftCodeService;
    @Inject elonmusk.service.IncomeService incomeService;
    @Inject javax.sql.DataSource dataSource;
    
    @POST
    @Path("/deposit/request")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response submitDepositRequest(@CookieParam("user_id") String userId,
                                       @FormParam("amount") String amountStr,
                                       @FormParam("transactionId") String transactionId,
                                       @FormParam("paymentMethod") String paymentMethod) {
        try {
            User user = getAuthenticatedUser(userId);
            
            // Validate payment method first
            if (paymentMethod == null || paymentMethod.trim().isEmpty()) {
                return Response.seeOther(java.net.URI.create("/recharge?error=Please+select+payment+method")).build();
            }
            
            if (!"telebirr".equals(paymentMethod) && !"paypal".equals(paymentMethod)) {
                return Response.seeOther(java.net.URI.create("/recharge?error=Invalid+payment+method")).build();
            }
            
            // Validate amount second
            if (amountStr == null || amountStr.trim().isEmpty()) {
                return Response.seeOther(java.net.URI.create("/recharge?error=Please+enter+amount")).build();
            }
            
            BigDecimal amount;
            try {
                amount = new BigDecimal(amountStr.trim());
                if (amount.compareTo(new BigDecimal("1.00")) < 0) {
                    return Response.seeOther(java.net.URI.create("/recharge?error=Minimum+deposit+is+$1.00")).build();
                }
                if (amount.compareTo(new BigDecimal("10000.00")) > 0) {
                    return Response.seeOther(java.net.URI.create("/recharge?error=Maximum+deposit+is+$10,000.00")).build();
                }
            } catch (NumberFormatException e) {
                return Response.seeOther(java.net.URI.create("/recharge?error=Invalid+amount+format")).build();
            }
            
            // Validate transaction ID third (format: CCL76AXGT4)
            if (transactionId == null || transactionId.trim().isEmpty()) {
                return Response.seeOther(java.net.URI.create("/recharge?error=Please+enter+transaction+ID")).build();
            }
            
            InputValidator.validateTransactionId(transactionId);
            
            // Create deposit request
            DepositRequest deposit = new DepositRequest();
            deposit.userId = user.id;
            deposit.amount = amount;
            deposit.paymentMethod = SecurityUtil.sanitizeInput(paymentMethod.trim());
            deposit.transactionId = SecurityUtil.sanitizeInput(transactionId.trim().toUpperCase());
            deposit.status = "PENDING";
            
            // Save to database
            boolean saved = depositService.createDepositRequest(deposit);
            
            if (saved) {
                logger.info("Deposit request created: User " + user.id + " Amount: $" + amount + " Method: " + paymentMethod + " TxnID: " + transactionId);
                return Response.seeOther(java.net.URI.create("/my?success=Deposit+request+submitted.+Check+Deposit+Status+section.")).build();
            } else {
                return Response.seeOther(java.net.URI.create("/my?error=Failed+to+submit+deposit+request")).build();
            }
            
        } catch (ValidationException e) {
            logger.warning("Deposit request validation failed: " + e.getMessage());
            String errorMsg = e.getMessage().replace(" ", "+");
            return Response.seeOther(java.net.URI.create("/my?error=" + errorMsg)).build();
        } catch (Exception e) {
            logger.severe("Deposit request error: " + e.getMessage());
            return Response.seeOther(java.net.URI.create("/my?error=Request+failed+please+try+again")).build();
        }
    }
    
    private java.util.List<java.util.Map<String, Object>> getReferralHistory(Long userId) {
        java.util.List<java.util.Map<String, Object>> history = new java.util.ArrayList<>();
        
        String sql = "SELECT u.name, u.phone_number, ri.created_at, ra.processed_at " +
                    "FROM referral_invitations ri " +
                    "JOIN users u ON ri.referred_user_id = u.id " +
                    "LEFT JOIN referral_acceptances ra ON ra.referred_user_id = u.id AND ra.referrer_id = ri.referrer_id " +
                    "WHERE ri.referrer_id = ? AND ri.status = 'ACTIVE' " +
                    "ORDER BY ri.created_at DESC LIMIT 10";
        
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, userId);
            java.sql.ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                java.util.Map<String, Object> referral = new java.util.HashMap<>();
                referral.put("name", rs.getString("name"));
                referral.put("phone", rs.getString("phone_number"));
                referral.put("joinedAt", rs.getTimestamp("created_at"));
                referral.put("acceptedAt", rs.getTimestamp("processed_at"));
                history.add(referral);
            }
            
        } catch (Exception e) {
            logger.warning("Failed to get referral history: " + e.getMessage());
        }
        
        return history;
    }
    
    private User getAuthenticatedUser(String userId) {
        logger.info("Authenticating user with ID: " + userId);
        if (userId == null || userId.trim().isEmpty()) {
            logger.warning("User ID is null or empty");
            throw new WebApplicationException(Response.seeOther(java.net.URI.create("/login")).build());
        }
        try {
            Long userIdLong = Long.parseLong(userId.trim());
            if (userIdLong <= 0) {
                logger.warning("User ID is invalid: " + userIdLong);
                throw new WebApplicationException(Response.seeOther(java.net.URI.create("/login")).build());
            }
            User user = userService.findById(userIdLong);
            if (user == null) {
                logger.warning("User not found for ID: " + userIdLong);
                throw new WebApplicationException(Response.seeOther(java.net.URI.create("/login")).build());
            }
            logger.info("User authenticated successfully: " + user.id);
            return user;
        } catch (NumberFormatException e) {
            logger.warning("Invalid user ID format: " + userId);
            throw new WebApplicationException(Response.seeOther(java.net.URI.create("/login")).build());
        } catch (Exception e) {
            logger.severe("Authentication error: " + e.getMessage());
            throw new WebApplicationException(Response.seeOther(java.net.URI.create("/login")).build());
        }
    }
    
    @GET
    @Path("/")
    public Response index() {
        return Response.seeOther(java.net.URI.create("/login")).build();
    }
    
    @GET
    @Path("/login")
    public Response loginPage(@QueryParam("error") String error,
                            @QueryParam("success") String success,
                            @QueryParam("returnUrl") String returnUrl) {
        TemplateInstance template = login.data("error", SecurityUtil.sanitizeInput(error))
                                        .data("success", SecurityUtil.sanitizeInput(success))
                                        .data("returnUrl", SecurityUtil.sanitizeInput(returnUrl));
        
        return Response.ok(template).build();
    }
    
    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response processLogin(@BeanParam LoginFormRequest request,
                               @FormParam("returnUrl") String returnUrl) {
        try {
            InputValidator.validatePhoneNumber(request.account);
            InputValidator.validatePassword(request.password);
            
            request.account = SecurityUtil.sanitizeInput(request.account);
            request.password = SecurityUtil.sanitizeInput(request.password);
            
            User user = authService.authenticate(request);
            authService.checkDailyBonus(user);
            
            NewCookie userCookie = new NewCookie.Builder("user_id")
                    .value(user.id.toString())
                    .path("/")
                    .maxAge(86400)
                    .httpOnly(true)
                    .sameSite(NewCookie.SameSite.LAX)
                    .build();
            
            logger.info("User logged in successfully: " + request.account);
            
            // Redirect to returnUrl if provided, otherwise to dashboard
            String redirectUrl = "/dashboard";
            if (returnUrl != null && !returnUrl.trim().isEmpty()) {
                returnUrl = SecurityUtil.sanitizeInput(returnUrl.trim());
                // Security check: only allow internal URLs
                if (returnUrl.startsWith("/") && !returnUrl.startsWith("//")) {
                    redirectUrl = returnUrl;
                }
            }
            
            return Response.seeOther(java.net.URI.create(redirectUrl))
                    .cookie(userCookie)
                    .build();
            
        } catch (ValidationException e) {
            logger.warning("Login validation failed for: " + request.account + " - " + e.getMessage());
            String errorMsg = e.getMessage().replace(" ", "+");
            String loginUrl = "/login?error=" + errorMsg;
            if (returnUrl != null && !returnUrl.trim().isEmpty()) {
                loginUrl += "&returnUrl=" + java.net.URLEncoder.encode(returnUrl, java.nio.charset.StandardCharsets.UTF_8);
            }
            return Response.seeOther(java.net.URI.create(loginUrl))
                    .build();
        } catch (ServiceException e) {
            logger.warning("Login service error for: " + request.account + " - " + e.getMessage());
            String errorMsg = e.getMessage().replace(" ", "+").replace(".", "");
            return Response.seeOther(java.net.URI.create("/login?error=" + errorMsg))
                    .build();
        } catch (Exception e) {
            logger.severe("Unexpected error during login: " + e.getMessage());
            return Response.seeOther(java.net.URI.create("/login?error=Login+failed+please+try+again"))
                    .build();
        }
    }

    @GET
    @Path("/signup")
    public Response signupPage(@QueryParam("error") String error,
                             @QueryParam("success") String success,
                             @QueryParam("ref") String referralCode) {
        TemplateInstance template = signup.data("error", SecurityUtil.sanitizeInput(error))
                                         .data("success", SecurityUtil.sanitizeInput(success))
                                         .data("referralCode", SecurityUtil.sanitizeInput(referralCode));
        
        return Response.ok(template).build();
    }

    @POST
    @Path("/signup")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response processSignup(@BeanParam RegisterFormRequest request) {
        try {
            logger.info("Registration attempt for: " + request.account);

            InputValidator.validateName(request.name);
            InputValidator.validatePhoneNumber(request.account);
            InputValidator.validateEmail(request.email);
            InputValidator.validatePassword(request.password);
            
            if (!request.password.equals(request.passwordRepeat)) {
                logger.warning("Registration failed: Password mismatch");
                return Response.seeOther(java.net.URI.create("/signup?error=Passwords+do+not+match")).build();
            }
            
            logger.info("Controller validation passed, creating user");
            User newUser = authService.register(request);
            
            // SIMPLIFIED: Automatically accept referral invitation (no manual acceptance needed)
            if (request.referralCode != null && !request.referralCode.trim().isEmpty()) {
                logger.info("Processing referral code from URL: " + request.referralCode);
                
                try {
                    String referralCode = request.referralCode.trim().toUpperCase();
                    
                    // Basic validation: Check if referrer exists and is not self
                    User referrer = userService.findByReferralCode(referralCode);
                    if (referrer != null && !referrer.id.equals(newUser.id)) {
                        // AUTOMATIC ACCEPTANCE: Directly accept the referral
                        referralAcceptanceService.acceptReferralAutomatically(newUser.id, referrer.id, referralCode);
                        logger.info("Referral automatically accepted for new user: " + newUser.id + " from referrer: " + referrer.id);
                    } else {
                        logger.warning("Invalid referral code or self-referral attempt: " + referralCode);
                        // Don't fail registration, just log the issue
                    }
                } catch (Exception e) {
                    logger.warning("Error processing referral code during signup: " + e.getMessage());
                    // Don't fail registration due to referral issues
                }
            }
            
            logger.info("Registration completed successfully");
            return Response.seeOther(java.net.URI.create("/login?success=Registration+successful"))
                    .build();
        } catch (ValidationException e) {
            logger.warning("Registration validation failed: " + e.getMessage());
            String errorMsg = e.getMessage().replace(" ", "+").replace(".", "");
            return Response.seeOther(java.net.URI.create("/signup?error=" + errorMsg))
                    .build();
        } catch (ServiceException e) {
            logger.warning("Registration service error: " + e.getMessage());
            String errorMsg = e.getMessage().replace(" ", "+").replace(".", "");
            return Response.seeOther(java.net.URI.create("/signup?error=" + errorMsg))
                    .build();
        } catch (Exception e) {
            logger.severe("Unexpected error during registration: " + e.getMessage());
            return Response.seeOther(java.net.URI.create("/signup?error=Registration+failed+please+try+again"))
                    .build();
        }
    }
    
    @GET
    @Path("/forgot-password")
    public Response forgotPasswordPage(@QueryParam("error") String error,
                                     @QueryParam("success") String success) {
        TemplateInstance template = forgotPassword.data("error", SecurityUtil.sanitizeInput(error))
                                                 .data("success", SecurityUtil.sanitizeInput(success));
        
        return Response.ok(template).build();
    }
    
    @POST
    @Path("/forgot-password")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response processForgotPassword(@FormParam("phone") String phone,
                                        @FormParam("newPassword") String newPassword) {
        try {
            if (phone == null || phone.trim().isEmpty()) {
                return Response.seeOther(java.net.URI.create("/forgot-password?error=Phone+number+required")).build();
            }
            
            if (newPassword == null || newPassword.trim().isEmpty()) {
                return Response.seeOther(java.net.URI.create("/forgot-password?error=New+password+required")).build();
            }
            
            InputValidator.validatePhoneNumber(phone);
            phone = SecurityUtil.sanitizeInput(phone.trim());
            newPassword = SecurityUtil.sanitizeInput(newPassword.trim());
            
            // Create password reset request with enhanced validation
            securityService.createPasswordResetRequest(phone, newPassword);
            
            logger.info("Password reset requested for: " + phone);
            return Response.seeOther(java.net.URI.create("/forgot-password?success=Password+reset+request+sent+to+admin+for+approval")).build();
            
        } catch (ValidationException e) {
            String errorMsg = e.getMessage().replace(" ", "+");
            return Response.seeOther(java.net.URI.create("/forgot-password?error=" + errorMsg)).build();
        } catch (Exception e) {
            logger.severe("Forgot password error: " + e.getMessage());
            return Response.seeOther(java.net.URI.create("/forgot-password?error=Request+failed")).build();
        }
    }
    
    @GET
    @Path("/dashboard")
    public Response dashboardPage(@CookieParam("user_id") String userId,
                                @QueryParam("error") String error,
                                @QueryParam("success") String success) {
        logger.info("Dashboard accessed with userId: " + userId);
        
        // Check authentication without throwing exceptions
        boolean isAuthenticated = false;
        String userName = "User";
        Long authenticatedUserId = null;
        boolean hasPendingReferral = false;
        
        if (userId != null && !userId.trim().isEmpty()) {
            try {
                Long userIdLong = Long.parseLong(userId.trim());
                if (userIdLong > 0) {
                    try (java.sql.Connection conn = dataSource.getConnection();
                         java.sql.PreparedStatement stmt = conn.prepareStatement("SELECT id, name FROM users WHERE id = ?")) {
                        stmt.setLong(1, userIdLong);
                        try (java.sql.ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                isAuthenticated = true;
                                authenticatedUserId = rs.getLong("id");
                                userName = rs.getString("name");
                            }
                        }
                    } catch (Exception e) {
                        logger.warning("Database error during authentication: " + e.getMessage());
                    }
                }
            } catch (NumberFormatException e) {
                logger.warning("Invalid user ID format: " + userId);
            }
        }

        if (!isAuthenticated) {
            return Response.seeOther(java.net.URI.create("/login")).build();
        }

        // Check for pending referral acceptance and get referrer info
        String referrerName = null;
        try {
            hasPendingReferral = referralAcceptanceService.hasPendingReferral(authenticatedUserId);
            if (hasPendingReferral) {
                // Get referrer name for display
                try (java.sql.Connection conn = dataSource.getConnection();
                     java.sql.PreparedStatement stmt = conn.prepareStatement(
                         "SELECT u.name FROM referral_acceptances r " +
                         "JOIN users u ON r.referrer_id = u.id " +
                         "WHERE r.referred_user_id = ? AND r.status = 'PENDING'")) {
                    stmt.setLong(1, authenticatedUserId);
                    try (java.sql.ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            referrerName = rs.getString("name");
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Error checking pending referral: " + e.getMessage());
        }

        // Create a simple User object for the template
        User user = new User();
        user.id = authenticatedUserId;
        user.name = userName;
        
        return Response.ok(home.data("user", user)
                              .data("hasPendingReferral", hasPendingReferral)
                              .data("referrerName", referrerName)
                              .data("error", error)
                              .data("success", success)).build();
    }
    
    @GET
    @Path("/home")
    public Response homePage(@CookieParam("user_id") String userId,
                           @QueryParam("error") String error,
                           @QueryParam("success") String success) {
        return dashboardPage(userId, error, success);
    }
    
    @GET
    @Path("/product")
    public TemplateInstance productPage(@CookieParam("user_id") String userId,
                                      @QueryParam("error") String error,
                                      @QueryParam("success") String success) {
        User user = getAuthenticatedUser(userId);
        List<elonmusk.model.InvestmentProduct> products = investmentService.getAllProducts();
        
        return product.data("user", user)
                     .data("products", products)
                     .data("error", error)
                     .data("success", success);
    }
    
    @POST
    @Path("/purchase")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response processPurchase(@CookieParam("user_id") String userId,
                                  @FormParam("productId") String productIdStr) {
        try {
            User user = getAuthenticatedUser(userId);
            
            if (productIdStr == null || productIdStr.trim().isEmpty()) {
                return Response.seeOther(java.net.URI.create("/product?error=Product+not+selected")).build();
            }
            
            Long productId;
            try {
                productId = Long.parseLong(productIdStr.trim());
            } catch (NumberFormatException e) {
                return Response.seeOther(java.net.URI.create("/product?error=Invalid+product+ID")).build();
            }
            
            if (productId <= 0) {
                return Response.seeOther(java.net.URI.create("/product?error=Invalid+product+ID")).build();
            }
            
            // Refresh user data to get latest wallet balance
            user = userService.findById(user.id);
            
            // Purchase the product
            elonmusk.model.UserInvestment investment = investmentService.purchaseProduct(user, productId);
            
            logger.info("Purchase successful: User " + userId + " bought product " + productId);
            return Response.seeOther(java.net.URI.create("/my?success=Investment+purchased+successfully")).build();
            
        } catch (elonmusk.exception.ValidationException e) {
            logger.warning("Purchase validation failed: " + e.getMessage());
            String errorMsg = e.getMessage().replace(" ", "+");
            return Response.seeOther(java.net.URI.create("/product?error=" + errorMsg)).build();
        } catch (elonmusk.exception.ServiceException e) {
            logger.warning("Purchase service error: " + e.getMessage());
            String errorMsg = e.getMessage().replace(" ", "+");
            return Response.seeOther(java.net.URI.create("/product?error=" + errorMsg)).build();
        } catch (Exception e) {
            logger.severe("Purchase error: " + e.getMessage());
            return Response.seeOther(java.net.URI.create("/product?error=Purchase+failed+please+try+again")).build();
        }
    }
    
    @GET
    @Path("/help")
    public TemplateInstance helpPage(@CookieParam("user_id") String userId) {
        User user = getAuthenticatedUser(userId);
        return help.data("user", user);
    }
    
    @GET
    @Path("/team")
    public TemplateInstance teamPage(@CookieParam("user_id") String userId) {
        User user = getAuthenticatedUser(userId);
        return team.data("user", user);
    }
    
    @POST
    @Path("/invite/send")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response sendInvite(@CookieParam("user_id") String userId,
                             @FormParam("phoneNumber") String phoneNumber) {
        try {
            User user = getAuthenticatedUser(userId);
            
            if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                return Response.seeOther(java.net.URI.create("/invite?error=Phone+number+required")).build();
            }
            
            InputValidator.validatePhoneNumber(phoneNumber);
            phoneNumber = SecurityUtil.sanitizeInput(phoneNumber.trim());
            
            // Log invitation attempt
            logger.info("Invitation sent by user " + userId + " to " + phoneNumber);
            return Response.seeOther(java.net.URI.create("/invite?success=Invitation+sent+successfully")).build();
            
        } catch (ValidationException e) {
            String errorMsg = e.getMessage().replace(" ", "+");
            return Response.seeOther(java.net.URI.create("/invite?error=" + errorMsg)).build();
        } catch (Exception e) {
            logger.severe("Invite error: " + e.getMessage());
            return Response.seeOther(java.net.URI.create("/invite?error=Failed+to+send+invitation")).build();
        }
    }
    
    @GET
    @Path("/my")
    public TemplateInstance myPage(@CookieParam("user_id") String userId,
                                 @QueryParam("success") String success,
                                 @QueryParam("error") String error) {
        User user = getAuthenticatedUser(userId);
        
        // Refresh user data to get latest balances
        user = userService.findById(user.id);
        
        List<elonmusk.model.DepositRequest> deposits = depositService.getUserDeposits(user.id);
        List<elonmusk.model.UserInvestment> investments = investmentService.getUserInvestments(user.id);
        List<elonmusk.model.WithdrawalRequest> withdrawals = withdrawalService.getUserWithdrawalHistory(user.id, 10);
        
        // Get deposit statistics
        java.util.Map<String, Object> depositStats = depositService.getDepositStats(user.id);
        
        return my.data("user", user)
                .data("investments", investments)
                .data("success", success)
                .data("error", error)
                .data("withdrawals", withdrawals)
                .data("deposits", deposits)
                .data("depositStats", depositStats);
    }
    
    @GET
    @Path("/recharge")
    public TemplateInstance rechargePage(@CookieParam("user_id") String userId,
                                       @QueryParam("error") String error,
                                       @QueryParam("success") String success) {
        User user = getAuthenticatedUser(userId);
        return deposit.data("user", user).data("error", error).data("success", success);
    }
    
    @POST
    @Path("/recharge")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response processRecharge(@CookieParam("user_id") String userId,
                                  @FormParam("amount") String amountStr,
                                  @FormParam("method") String method,
                                  @FormParam("transactionId") String transactionId) {
        try {
            User user = getAuthenticatedUser(userId);
            
            // Validate inputs
            if (amountStr == null || amountStr.trim().isEmpty()) {
                return Response.seeOther(java.net.URI.create("/recharge?error=Amount+is+required")).build();
            }
            
            java.math.BigDecimal amount;
            try {
                amount = new java.math.BigDecimal(amountStr);
            } catch (NumberFormatException e) {
                return Response.seeOther(java.net.URI.create("/recharge?error=Invalid+amount+format")).build();
            }
            
            InputValidator.validateAmount(amount, new java.math.BigDecimal("10.00"), new java.math.BigDecimal("75000.00"));
            InputValidator.validatePaymentMethod(method);
            InputValidator.validateTransactionId(transactionId);
            
            logger.info("Deposit request for user " + userId + ": " + amount + " ETB via " + method);
            return Response.seeOther(java.net.URI.create("/recharge?success=Deposit+request+submitted+successfully")).build();
                    
        } catch (ValidationException e) {
            logger.warning("Deposit validation failed: " + e.getMessage());
            String errorMsg = e.getMessage().replace(" ", "+");
            return Response.seeOther(java.net.URI.create("/recharge?error=" + errorMsg)).build();
        } catch (Exception e) {
            logger.severe("Unexpected error during deposit: " + e.getMessage());
            return Response.seeOther(java.net.URI.create("/recharge?error=Deposit+request+failed")).build();
        }
    }
    
    @GET
    @Path("/withdrawal")
    public TemplateInstance withdrawalPage(@CookieParam("user_id") String userId,
                                         @QueryParam("success") String success,
                                         @QueryParam("error") String error,
                                         @QueryParam("warning") String warning) {
        User user = getAuthenticatedUser(userId);
        
        // Get recent withdrawal history (last 10)
        List<elonmusk.model.WithdrawalRequest> withdrawalHistory = withdrawalService.getUserWithdrawalHistory(user.id, 10);
        
        return withdrawalRequest.data("user", user)
                               .data("success", success)
                               .data("error", error)
                               .data("warning", warning)
                               .data("withdrawalHistory", withdrawalHistory);
    }
    
    @POST
    @Path("/withdrawal")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response processWithdrawal(@CookieParam("user_id") String userId,
                                    @FormParam("amount") String amountStr,
                                    @FormParam("bankAccount") String bankAccount) {
        try {
            User user = getAuthenticatedUser(userId);
            
            // Validate inputs
            if (amountStr == null || amountStr.trim().isEmpty()) {
                return Response.seeOther(java.net.URI.create("/withdrawal?error=Amount+is+required")).build();
            }
            
            if (bankAccount == null || bankAccount.trim().isEmpty()) {
                return Response.seeOther(java.net.URI.create("/withdrawal?error=Bank+account+number+is+required")).build();
            }
            
            java.math.BigDecimal amount;
            try {
                amount = new java.math.BigDecimal(amountStr.trim());
            } catch (NumberFormatException e) {
                return Response.seeOther(java.net.URI.create("/withdrawal?error=Invalid+amount+format")).build();
            }
            
            // Create withdrawal request using service
            elonmusk.model.WithdrawalRequest request = withdrawalService.createWithdrawalRequest(user, amount, bankAccount);
            
            logger.info("Withdrawal request for user " + userId + ": " + amount + " ETB via " + bankAccount);
            return Response.seeOther(java.net.URI.create("/withdrawal?success=Withdrawal+request+submitted+successfully.+You+will+be+notified+once+processed.")).build();
            
        } catch (ValidationException e) {
            logger.warning("Withdrawal validation failed: " + e.getMessage());
            String errorMsg = e.getMessage().replace(" ", "+");
            return Response.seeOther(java.net.URI.create("/withdrawal?error=" + errorMsg)).build();
        } catch (ServiceException e) {
            logger.warning("Withdrawal service error: " + e.getMessage());
            return Response.seeOther(java.net.URI.create("/withdrawal?error=Unable+to+process+withdrawal+request")).build();
        } catch (Exception e) {
            logger.severe("Withdrawal error: " + e.getMessage());
            return Response.seeOther(java.net.URI.create("/withdrawal?error=Withdrawal+request+failed.+Please+try+again.")).build();
        }
    }
    
    @GET
    @Path("/withdrawals")
    public TemplateInstance withdrawalHistoryPage(@CookieParam("user_id") String userId) {
        User user = getAuthenticatedUser(userId);
        List<elonmusk.model.WithdrawalRequest> userWithdrawals = withdrawalService.getUserWithdrawalHistory(user.id, 50);
        return withdrawalHistory.data("user", user).data("withdrawalHistory", userWithdrawals);
    }
    
    @GET
    @Path("/logout")
    public Response logout() {
        NewCookie clearCookie = new NewCookie.Builder("user_id")
                .value("")
                .path("/")
                .maxAge(0)
                .build();
                
        return Response.seeOther(java.net.URI.create("/login"))
                .cookie(clearCookie)
                .build();
    }
    
    // Handle common static resource requests to prevent 404s
    @GET
    @Path("/css/{filename}")
    public Response getCss(@PathParam("filename") String filename) {
        return Response.status(404).build();
    }
    
    @GET
    @Path("/js/{filename}")
    public Response getJs(@PathParam("filename") String filename) {
        // Handle language.js specifically
        if ("language.js".equals(filename)) {
            try {
                java.io.InputStream jsStream = getClass().getClassLoader()
                    .getResourceAsStream("META-INF/resources/js/language.js");
                if (jsStream != null) {
                    return Response.ok(jsStream, "application/javascript")
                        .header("Cache-Control", "public, max-age=3600")
                        .build();
                }
            } catch (Exception e) {
                logger.warning("Error serving language.js: " + e.getMessage());
            }
        }
        return Response.status(404).build();
    }
    
    @GET
    @Path("/favicon.ico")
    public Response getFavicon() {
        return Response.status(404).build();
    }
    
    // Admin route redirects
    @GET
    @Path("/admin-dashboard")
    public Response adminDashboardRedirect() {
        return Response.seeOther(java.net.URI.create("/million/dashboard")).build();
    }
    
    @GET
    @Path("/admin-users")
    public Response adminUsersRedirect() {
        return Response.seeOther(java.net.URI.create("/million/users")).build();
    }
    

    @GET
    @Path("/admin-logout")
    public Response adminLogoutRedirect() {
        return Response.seeOther(java.net.URI.create("/million/logout")).build();
    }
    
    @GET
    @Path("/gift")
    public Response giftPage(@CookieParam("user_id") String userId,
                           @QueryParam("success") String success,
                           @QueryParam("error") String error) {
        
        // Check authentication first
        boolean isAuthenticated = false;
        String userName = "User";
        
        if (userId != null && !userId.trim().isEmpty()) {
            try {
                Long userIdLong = Long.parseLong(userId.trim());
                if (userIdLong > 0) {
                    // Direct database check
                    try (java.sql.Connection conn = dataSource.getConnection();
                         java.sql.PreparedStatement stmt = conn.prepareStatement("SELECT name FROM users WHERE id = ?")) {
                        stmt.setLong(1, userIdLong);
                        try (java.sql.ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                isAuthenticated = true;
                                userName = rs.getString("name");
                            }
                        }
                    } catch (Exception e) {
                        // Ignore database errors, just show not authenticated
                    }
                }
            } catch (NumberFormatException e) {
                // Ignore invalid user ID format
            }
        }

        if (!isAuthenticated) {
            // Redirect to login
            return Response.seeOther(java.net.URI.create("/login?returnUrl=/gift")).build();
        }

        // Enhanced HTML response with gift code input and multi-language support
        StringBuilder htmlContent = new StringBuilder();
        htmlContent.append("<!DOCTYPE html>");
        htmlContent.append("<html lang=\"en\">");
        htmlContent.append("<head>");
        htmlContent.append("<meta charset=\"UTF-8\">");
        htmlContent.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">");
        htmlContent.append("<title>Gift Codes</title>");
        htmlContent.append("<style>");
        htmlContent.append("* { box-sizing: border-box; margin: 0; padding: 0; font-family: Arial, sans-serif; }");
        htmlContent.append("body { background: linear-gradient(to right, #3d82e9, #60b966); min-height: 100vh; display: flex; flex-direction: column; align-items: center; }");
        htmlContent.append(".header { color: white; padding: 1.5rem; text-align: center; width: 100%; background-color: #5f5fbc; box-shadow: 0 2px 5px rgba(0, 0, 0, 0.1); position: relative; }");
        htmlContent.append(".language-selector { position: absolute; top: 10px; right: 20px; }");
        htmlContent.append(".language-btn { background: rgba(255,255,255,0.2); color: white; border: 1px solid rgba(255,255,255,0.3); padding: 5px 10px; margin: 0 2px; border-radius: 15px; cursor: pointer; font-size: 12px; }");
        htmlContent.append(".language-btn.active { background: #d4b344; border-color: #d4b344; }");
        htmlContent.append(".main-container { padding: 2rem; display: flex; flex-direction: column; align-items: center; width: 100%; max-width: 700px; }");
        htmlContent.append(".gift-container { background-color: #5858c1; padding: 20px; margin-top: 20px; color: #efedf5; border-radius: 10px; box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1); width: 100%; }");
        htmlContent.append(".stats-container { display: grid; grid-template-columns: 1fr 1fr; gap: 15px; margin-bottom: 20px; }");
        htmlContent.append(".stat-box { background-color: #242163; padding: 15px; border-radius: 8px; text-align: center; }");
        htmlContent.append(".stat-value { font-size: 1.5em; font-weight: bold; color: #d4b344; }");
        htmlContent.append(".stat-label { font-size: 0.9em; color: #ccc; margin-top: 5px; }");
        htmlContent.append(".redeem-form { background-color: #333394; padding: 20px; border-radius: 8px; margin-bottom: 20px; }");
        htmlContent.append(".form-group { margin-bottom: 15px; }");
        htmlContent.append(".form-group label { display: block; font-weight: bold; margin-bottom: 8px; color: white; }");
        htmlContent.append(".form-group input { width: 100%; padding: 12px; border: 1px solid #ddd; border-radius: 5px; background-color: #242163; color: white; font-size: 16px; text-transform: uppercase; letter-spacing: 1px; font-family: monospace; }");
        htmlContent.append(".form-group input::placeholder { color: rgba(255, 255, 255, 0.7); text-transform: none; letter-spacing: normal; font-family: Arial, sans-serif; }");
        htmlContent.append(".redeem-button { width: 100%; padding: 12px; background-color: #d4b344; color: white; border: none; border-radius: 5px; font-weight: bold; cursor: pointer; font-size: 16px; }");
        htmlContent.append(".redeem-button:hover { background-color: #c0a040; }");
        htmlContent.append(".message { padding: 15px; margin: 10px 0; border-radius: 8px; text-align: center; font-weight: bold; }");
        htmlContent.append(".success { background-color: rgba(76, 175, 80, 0.2); color: #4CAF50; border: 1px solid #4CAF50; }");
        htmlContent.append(".error { background-color: rgba(231, 76, 60, 0.2); color: #e74c3c; border: 1px solid #e74c3c; }");
        htmlContent.append(".info-section { background-color: #333394; padding: 20px; border-radius: 8px; margin: 20px 0; border-left: 4px solid #d4b344; }");
        htmlContent.append(".section-title { font-size: 1.2em; font-weight: bold; margin-bottom: 15px; color: #d4b344; }");
        htmlContent.append(".back-link { display: block; text-align: center; color: #d4b344; text-decoration: none; margin-top: 1rem; padding: 10px; background-color: #333394; border-radius: 8px; width: 100%; }");
        htmlContent.append(".back-link:hover { background-color: #242163; }");
        htmlContent.append(".lang-text { display: none; }");
        htmlContent.append(".lang-text.active { display: inline; }");
        htmlContent.append("@media screen and (max-width: 412px) { .stats-container { grid-template-columns: 1fr; } .language-selector { position: static; text-align: center; margin-bottom: 10px; } }");
        htmlContent.append("</style>");
        htmlContent.append("</head>");
        htmlContent.append("<body>");
        
        // Header with language selector
        htmlContent.append("<header class=\"header\">");
        htmlContent.append("<div class=\"language-selector\">");
        htmlContent.append("<button class=\"language-btn active\" onclick=\"switchLanguage('en')\">EN</button>");
        htmlContent.append("<button class=\"language-btn\" onclick=\"switchLanguage('am')\">አማ</button>");
        htmlContent.append("<button class=\"language-btn\" onclick=\"switchLanguage('zh')\">中文</button>");
        htmlContent.append("<button class=\"language-btn\" onclick=\"switchLanguage('es')\">ES</button>");
        htmlContent.append("</div>");
        htmlContent.append("<h1>🎁 ");
        htmlContent.append("<span class=\"lang-text active\" data-lang=\"en\">Gift Codes</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"am\">የስጦታ ኮዶች</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"zh\">礼品代码</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"es\">Códigos de Regalo</span>");
        htmlContent.append("</h1>");
        htmlContent.append("<p>");
        htmlContent.append("<span class=\"lang-text active\" data-lang=\"en\">Redeem special codes to earn rewards</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"am\">ሽልማቶችን ለማግኘት ልዩ ኮዶችን ይለቀቁ</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"zh\">兑换特殊代码以获得奖励</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"es\">Canjea códigos especiales para ganar recompensas</span>");
        htmlContent.append("</p>");
        htmlContent.append("</header>");
        
        htmlContent.append("<div class=\"main-container\">");
        htmlContent.append("<div class=\"gift-container\">");

        // Add success/error messages if present
        if (success != null && !success.trim().isEmpty()) {
            htmlContent.append("<div class=\"message success\">").append(success.replace("+", " ")).append("</div>");
        }
        if (error != null && !error.trim().isEmpty()) {
            htmlContent.append("<div class=\"message error\">").append(error.replace("+", " ")).append("</div>");
        }

        // Get actual user statistics from database
        String totalEarned = "$0";
        String codesRedeemed = "0";
        
        if (userId != null && !userId.trim().isEmpty()) {
            try {
                Long userIdLong = Long.parseLong(userId.trim());
                if (userIdLong > 0) {
                    // Get total gift earnings
                    try (java.sql.Connection conn = dataSource.getConnection();
                         java.sql.PreparedStatement stmt = conn.prepareStatement("SELECT COALESCE(SUM(amount), 0) as total FROM gift_code_redemptions WHERE user_id = ?")) {
                        stmt.setLong(1, userIdLong);
                        try (java.sql.ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                java.math.BigDecimal total = rs.getBigDecimal("total");
                                totalEarned = "$" + total.toString();
                            }
                        }
                    } catch (Exception e) {
                        logger.warning("Error getting total earnings: " + e.getMessage());
                    }
                    
                    // Get count of redeemed codes
                    try (java.sql.Connection conn = dataSource.getConnection();
                         java.sql.PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) as count FROM gift_code_redemptions WHERE user_id = ?")) {
                        stmt.setLong(1, userIdLong);
                        try (java.sql.ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                codesRedeemed = String.valueOf(rs.getInt("count"));
                            }
                        }
                    } catch (Exception e) {
                        logger.warning("Error getting codes count: " + e.getMessage());
                    }
                }
            } catch (NumberFormatException e) {
                // Ignore invalid user ID format
            }
        }

        // Stats section
        htmlContent.append("<div class=\"stats-container\">");
        htmlContent.append("<div class=\"stat-box\">");
        htmlContent.append("<div class=\"stat-value\">").append(totalEarned).append("</div>");
        htmlContent.append("<div class=\"stat-label\">");
        htmlContent.append("<span class=\"lang-text active\" data-lang=\"en\">Total Earned</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"am\">ጠቅላላ የተገኘ</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"zh\">总收入</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"es\">Total Ganado</span>");
        htmlContent.append("</div>");
        htmlContent.append("</div>");
        htmlContent.append("<div class=\"stat-box\">");
        htmlContent.append("<div class=\"stat-value\">").append(codesRedeemed).append("</div>");
        htmlContent.append("<div class=\"stat-label\">");
        htmlContent.append("<span class=\"lang-text active\" data-lang=\"en\">Codes Redeemed</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"am\">የተለቀቁ ኮዶች</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"zh\">已兑换代码</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"es\">Códigos Canjeados</span>");
        htmlContent.append("</div>");
        htmlContent.append("</div>");
        htmlContent.append("</div>");

        // Gift code redemption form
        htmlContent.append("<form action=\"/gift/redeem\" method=\"post\" class=\"redeem-form\">");
        htmlContent.append("<div class=\"form-group\">");
        htmlContent.append("<label for=\"giftCode\">");
        htmlContent.append("<span class=\"lang-text active\" data-lang=\"en\">Enter Gift Code:</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"am\">የስጦታ ኮድ ያስገቡ:</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"zh\">输入礼品代码:</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"es\">Ingresa el Código de Regalo:</span>");
        htmlContent.append("</label>");
        htmlContent.append("<input type=\"text\" id=\"giftCode\" name=\"giftCode\" ");
        htmlContent.append("placeholder=\"ABC123XY\" maxlength=\"8\" required>");
        htmlContent.append("</div>");
        htmlContent.append("<button type=\"submit\" class=\"redeem-button\">");
        htmlContent.append("🎁 <span class=\"lang-text active\" data-lang=\"en\">Redeem Code</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"am\">ኮድ ይለቀቁ</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"zh\">兑换代码</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"es\">Canjear Código</span>");
        htmlContent.append("</button>");
        htmlContent.append("</form>");

        // Welcome message
        htmlContent.append("<div style=\"text-align: center; padding: 20px;\">");
        htmlContent.append("<h2>");
        htmlContent.append("<span class=\"lang-text active\" data-lang=\"en\">Welcome, ").append(userName).append("!</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"am\">እንኳን ደህና መጡ, ").append(userName).append("!</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"zh\">欢迎, ").append(userName).append("!</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"es\">¡Bienvenido, ").append(userName).append("!</span>");
        htmlContent.append("</h2>");
        htmlContent.append("</div>");
        
        // Instructions section
        htmlContent.append("<div class=\"info-section\">");
        htmlContent.append("<div class=\"section-title\">");
        htmlContent.append("📋 <span class=\"lang-text active\" data-lang=\"en\">How to Use Gift Codes</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"am\">የስጦታ ኮዶችን እንዴት መጠቀም እንደሚቻል</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"zh\">如何使用礼品代码</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"es\">Cómo Usar los Códigos de Regalo</span>");
        htmlContent.append("</div>");
        htmlContent.append("<div style=\"font-size: 0.9em; line-height: 1.6; color: #ccc;\">");
        
        // Instructions in multiple languages
        htmlContent.append("<div class=\"lang-text active\" data-lang=\"en\">");
        htmlContent.append("<p><strong>1.</strong> Get gift codes from admin announcements or Telegram</p>");
        htmlContent.append("<p><strong>2.</strong> Copy and paste the 8-character code above</p>");
        htmlContent.append("<p><strong>3.</strong> Click \"Redeem Code\" to add money to your account</p>");
        htmlContent.append("<p><strong>4.</strong> Codes expire after admin-set time</p>");
        htmlContent.append("<p><strong>5.</strong> Each code can only be used once per user</p>");
        htmlContent.append("</div>");
        
        htmlContent.append("<div class=\"lang-text\" data-lang=\"am\">");
        htmlContent.append("<p><strong>1.</strong> ከአስተዳዳሪ ማስታወቂያዎች ወይም ቴሌግራም የስጦታ ኮዶችን ያግኙ</p>");
        htmlContent.append("<p><strong>2.</strong> ከላይ ያለውን የ8-ቁምፊ ኮድ ይቅዱ እና ይለጥፉ</p>");
        htmlContent.append("<p><strong>3.</strong> ወደ መለያዎ ገንዘብ ለመጨመር \"ኮድ ይለቀቁ\" ን ይጫኑ</p>");
        htmlContent.append("<p><strong>4.</strong> ኮዶች ከአስተዳዳሪ-የተቀመጠ ጊዜ በኋላ ይጠፋሉ</p>");
        htmlContent.append("<p><strong>5.</strong> እያንዳንዱ ኮድ በአንድ ተጠቃሚ አንድ ጊዜ ብቻ ሊጠቀም ይችላል</p>");
        htmlContent.append("</div>");
        
        htmlContent.append("<div class=\"lang-text\" data-lang=\"zh\">");
        htmlContent.append("<p><strong>1.</strong> 从管理员公告或Telegram获取礼品代码</p>");
        htmlContent.append("<p><strong>2.</strong> 复制并粘贴上面的8字符代码</p>");
        htmlContent.append("<p><strong>3.</strong> 点击\"兑换代码\"将钱添加到您的账户</p>");
        htmlContent.append("<p><strong>4.</strong> 代码在管理员设定的时间后过期</p>");
        htmlContent.append("<p><strong>5.</strong> 每个代码每个用户只能使用一次</p>");
        htmlContent.append("</div>");
        
        htmlContent.append("<div class=\"lang-text\" data-lang=\"es\">");
        htmlContent.append("<p><strong>1.</strong> Obtén códigos de regalo de anuncios del admin o Telegram</p>");
        htmlContent.append("<p><strong>2.</strong> Copia y pega el código de 8 caracteres arriba</p>");
        htmlContent.append("<p><strong>3.</strong> Haz clic en \"Canjear Código\" para agregar dinero a tu cuenta</p>");
        htmlContent.append("<p><strong>4.</strong> Los códigos expiran después del tiempo establecido por el admin</p>");
        htmlContent.append("<p><strong>5.</strong> Cada código solo puede ser usado una vez por usuario</p>");
        htmlContent.append("</div>");
        
        htmlContent.append("</div>");
        htmlContent.append("</div>");
        htmlContent.append("</div>");
        
        // Back link
        htmlContent.append("<a href=\"/dashboard\" class=\"back-link\">");
        htmlContent.append("← <span class=\"lang-text active\" data-lang=\"en\">Back to Dashboard</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"am\">ወደ ዳሽቦርድ ተመለስ</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"zh\">返回仪表板</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"es\">Volver al Panel</span>");
        htmlContent.append("</a>");
        htmlContent.append("</div>");

        // JavaScript for language switching and form validation
        htmlContent.append("<script>");
        htmlContent.append("let currentLang = 'en';");
        htmlContent.append("function switchLanguage(lang) {");
        htmlContent.append("  currentLang = lang;");
        htmlContent.append("  document.querySelectorAll('.lang-text').forEach(el => el.classList.remove('active'));");
        htmlContent.append("  document.querySelectorAll('.lang-text[data-lang=\"' + lang + '\"]').forEach(el => el.classList.add('active'));");
        htmlContent.append("  document.querySelectorAll('.language-btn').forEach(btn => btn.classList.remove('active'));");
        htmlContent.append("  event.target.classList.add('active');");
        htmlContent.append("  localStorage.setItem('selectedLanguage', lang);");
        htmlContent.append("}");
        htmlContent.append("document.addEventListener('DOMContentLoaded', function() {");
        htmlContent.append("  const savedLang = localStorage.getItem('selectedLanguage') || 'en';");
        htmlContent.append("  if (savedLang !== 'en') {");
        htmlContent.append("    const langBtn = document.querySelector('.language-btn[onclick*=\"' + savedLang + '\"]');");
        htmlContent.append("    if (langBtn) langBtn.click();");
        htmlContent.append("  }");
        htmlContent.append("});");
        htmlContent.append("document.getElementById('giftCode').addEventListener('input', function(e) {");
        htmlContent.append("  let value = e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, '');");
        htmlContent.append("  if (value.length > 8) value = value.substring(0, 8);");
        htmlContent.append("  e.target.value = value;");
        htmlContent.append("  if (value.length === 8) {");
        htmlContent.append("    e.target.style.borderColor = '#4CAF50';");
        htmlContent.append("  } else {");
        htmlContent.append("    e.target.style.borderColor = '#ddd';");
        htmlContent.append("  }");
        htmlContent.append("});");
        htmlContent.append("document.querySelector('.redeem-form').addEventListener('submit', function(e) {");
        htmlContent.append("  const code = document.getElementById('giftCode').value.trim();");
        htmlContent.append("  if (code.length !== 8) {");
        htmlContent.append("    e.preventDefault();");
        htmlContent.append("    const messages = {");
        htmlContent.append("      'en': 'Gift code must be exactly 8 characters',");
        htmlContent.append("      'am': 'የስጦታ ኮድ በትክክል 8 ቁምፊዎች መሆን አለበት',");
        htmlContent.append("      'zh': '礼品代码必须正好是8个字符',");
        htmlContent.append("      'es': 'El código de regalo debe tener exactamente 8 caracteres'");
        htmlContent.append("    };");
        htmlContent.append("    alert(messages[currentLang] || messages['en']);");
        htmlContent.append("    return false;");
        htmlContent.append("  }");
        htmlContent.append("  if (!/^[A-Z0-9]{8}$/.test(code)) {");
        htmlContent.append("    e.preventDefault();");
        htmlContent.append("    const messages = {");
        htmlContent.append("      'en': 'Gift code must contain only uppercase letters (A-Z) and numbers (0-9)',");
        htmlContent.append("      'am': 'የስጦታ ኮድ ከላይኛ ፊደላት (A-Z) እና ቁጥሮች (0-9) ብቻ መያዝ አለበት',");
        htmlContent.append("      'zh': '礼品代码只能包含大写字母(A-Z)和数字(0-9)',");
        htmlContent.append("      'es': 'El código de regalo debe contener solo letras mayúsculas (A-Z) y números (0-9)'");
        htmlContent.append("    };");
        htmlContent.append("    alert(messages[currentLang] || messages['en']);");
        htmlContent.append("    return false;");
        htmlContent.append("  }");
        htmlContent.append("});");
        htmlContent.append("</script>");
        
        htmlContent.append("</body>");
        htmlContent.append("</html>");

        return Response.ok(htmlContent.toString(), "text/html").build();
    }
    
    @POST
    @Path("/gift/redeem")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response redeemGiftCode(@CookieParam("user_id") String userId,
                                 @FormParam("giftCode") String giftCode) {
        
        // Check authentication first
        Long authenticatedUserId = null;
        if (userId != null && !userId.trim().isEmpty()) {
            try {
                Long userIdLong = Long.parseLong(userId.trim());
                if (userIdLong > 0) {
                    try (java.sql.Connection conn = dataSource.getConnection();
                         java.sql.PreparedStatement stmt = conn.prepareStatement("SELECT id FROM users WHERE id = ?")) {
                        stmt.setLong(1, userIdLong);
                        try (java.sql.ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                authenticatedUserId = userIdLong;
                            }
                        }
                    } catch (Exception e) {
                        logger.warning("Database error during authentication: " + e.getMessage());
                    }
                }
            } catch (NumberFormatException e) {
                logger.warning("Invalid user ID format: " + userId);
            }
        }
        
        if (authenticatedUserId == null) {
            return Response.seeOther(java.net.URI.create("/login?returnUrl=/gift&error=Please+login+to+redeem+gift+codes")).build();
        }
        
        // Validate gift code input
        if (giftCode == null || giftCode.trim().isEmpty()) {
            return Response.seeOther(java.net.URI.create("/gift?error=Please+enter+a+gift+code")).build();
        }
        
        String cleanCode = giftCode.trim().toUpperCase();
        logger.info("Attempting to redeem gift code: " + cleanCode + " for user: " + authenticatedUserId);
        
        // Basic validation
        if (cleanCode.length() != 8) {
            return Response.seeOther(java.net.URI.create("/gift?error=Gift+code+must+be+exactly+8+characters")).build();
        }
        
        if (!cleanCode.matches("^[A-Z0-9]{8}$")) {
            return Response.seeOther(java.net.URI.create("/gift?error=Invalid+code+format.+Use+only+letters+and+numbers")).build();
        }
        
        // Use the actual GiftCodeService to redeem the code
        try {
            boolean redeemed = giftCodeService.redeemGiftCode(authenticatedUserId, cleanCode);
            
            if (redeemed) {
                logger.info("Gift code redeemed successfully: " + cleanCode + " by user: " + authenticatedUserId);
                return Response.seeOther(java.net.URI.create("/gift?success=Gift+code+redeemed+successfully!+Check+your+wallet+balance.")).build();
            } else {
                logger.warning("Gift code redemption failed: " + cleanCode + " for user: " + authenticatedUserId);
                return Response.seeOther(java.net.URI.create("/gift?error=Failed+to+redeem+gift+code")).build();
            }
            
        } catch (elonmusk.exception.ValidationException e) {
            String errorMsg = sanitizeErrorMessage(e.getMessage());
            logger.warning("Gift code redemption validation error: " + e.getMessage());
            return Response.seeOther(java.net.URI.create("/gift?error=" + errorMsg)).build();
        } catch (Exception e) {
            logger.severe("Error redeeming gift code: " + e.getMessage());
            e.printStackTrace();
            return Response.seeOther(java.net.URI.create("/gift?error=Gift+code+redemption+failed")).build();
        }
    }
    
    private String sanitizeErrorMessage(String message) {
        if (message == null) return "Operation+failed";
        
        // Remove sensitive information
        String sanitized = message
            .replaceAll("(?i)database", "system")
            .replaceAll("(?i)sql", "data")
            .replaceAll("(?i)connection", "service")
            .replaceAll("(?i)table", "record")
            .replaceAll("(?i)column", "field")
            .replace(" ", "+");
            
        return sanitized;
    }
    
    @GET
    @Path("/invite")
    public Response invitePage(@CookieParam("user_id") String userId,
                             @QueryParam("success") String success,
                             @QueryParam("error") String error) {
        
        // Check authentication first
        boolean isAuthenticated = false;
        String userName = "User";
        String referralCode = "";
        Long authenticatedUserId = null;
        
        if (userId != null && !userId.trim().isEmpty()) {
            try {
                Long userIdLong = Long.parseLong(userId.trim());
                if (userIdLong > 0) {
                    // Direct database check
                    try (java.sql.Connection conn = dataSource.getConnection();
                         java.sql.PreparedStatement stmt = conn.prepareStatement("SELECT id, name, referral_code FROM users WHERE id = ?")) {
                        stmt.setLong(1, userIdLong);
                        try (java.sql.ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                isAuthenticated = true;
                                authenticatedUserId = rs.getLong("id");
                                userName = rs.getString("name");
                                referralCode = rs.getString("referral_code");
                            }
                        }
                    } catch (Exception e) {
                        logger.warning("Database error during authentication: " + e.getMessage());
                    }
                }
            } catch (NumberFormatException e) {
                logger.warning("Invalid user ID format: " + userId);
            }
        }

        if (!isAuthenticated) {
            // Redirect to login
            return Response.seeOther(java.net.URI.create("/login?returnUrl=/invite")).build();
        }

        // Generate proper referral link
        String baseUrl = "http://localhost:8080"; // Replace with actual domain
        String referralLink = baseUrl + "/signup?ref=" + referralCode;
        
        // Get referral statistics from database
        int totalReferrals = 0;
        java.math.BigDecimal referralEarnings = java.math.BigDecimal.ZERO;
        
        try {
            // Get total referrals count
            try (java.sql.Connection conn = dataSource.getConnection();
                 java.sql.PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) as count FROM referral_invitations WHERE referrer_id = ? AND status = 'ACTIVE'")) {
                stmt.setLong(1, authenticatedUserId);
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        totalReferrals = rs.getInt("count");
                    }
                }
            }
            
            // Get total referral earnings
            try (java.sql.Connection conn = dataSource.getConnection();
                 java.sql.PreparedStatement stmt = conn.prepareStatement("SELECT COALESCE(SUM(bonus_amount), 0) as total FROM referral_bonuses WHERE referrer_id = ?")) {
                stmt.setLong(1, authenticatedUserId);
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        referralEarnings = rs.getBigDecimal("total");
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Error loading referral statistics: " + e.getMessage());
        }

        // Enhanced HTML response with multi-language support
        StringBuilder htmlContent = new StringBuilder();
        htmlContent.append("<!DOCTYPE html>");
        htmlContent.append("<html lang=\"en\">");
        htmlContent.append("<head>");
        htmlContent.append("<meta charset=\"UTF-8\">");
        htmlContent.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">");
        htmlContent.append("<title>Invite Friends</title>");
        htmlContent.append("<style>");
        htmlContent.append("* { box-sizing: border-box; margin: 0; padding: 0; font-family: Arial, sans-serif; }");
        htmlContent.append("body { background: linear-gradient(to right, #3d82e9, #60b966); min-height: 100vh; display: flex; flex-direction: column; align-items: center; }");
        htmlContent.append(".header { color: white; padding: 1.5rem; text-align: center; width: 100%; background-color: #5f5fbc; box-shadow: 0 2px 5px rgba(0, 0, 0, 0.1); position: relative; }");
        htmlContent.append(".language-selector { position: absolute; top: 10px; right: 20px; }");
        htmlContent.append(".language-btn { background: rgba(255,255,255,0.2); color: white; border: 1px solid rgba(255,255,255,0.3); padding: 5px 10px; margin: 0 2px; border-radius: 15px; cursor: pointer; font-size: 12px; }");
        htmlContent.append(".language-btn.active { background: #d4b344; border-color: #d4b344; }");
        htmlContent.append(".main-container { padding: 2rem; display: flex; flex-direction: column; align-items: center; width: 100%; max-width: 700px; }");
        htmlContent.append(".invite-container { background-color: #5858c1; padding: 20px; margin-top: 20px; color: #efedf5; border-radius: 10px; box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1); width: 100%; }");
        htmlContent.append(".stats-container { display: grid; grid-template-columns: 1fr 1fr; gap: 15px; margin-bottom: 20px; }");
        htmlContent.append(".stat-box { background-color: #242163; padding: 15px; border-radius: 8px; text-align: center; }");
        htmlContent.append(".stat-value { font-size: 1.5em; font-weight: bold; color: #d4b344; }");
        htmlContent.append(".stat-label { font-size: 0.9em; color: #ccc; margin-top: 5px; }");
        htmlContent.append(".referral-section { background-color: #333394; padding: 20px; border-radius: 8px; margin: 20px 0; border-left: 4px solid #d4b344; }");
        htmlContent.append(".section-title { font-size: 1.2em; font-weight: bold; margin-bottom: 15px; color: #d4b344; }");
        htmlContent.append(".referral-link { background-color: #242163; padding: 15px; border-radius: 8px; margin: 15px 0; border: 1px solid #d4b344; }");
        htmlContent.append(".link-input { width: 100%; padding: 10px; background-color: #1a1a4a; color: white; border: none; border-radius: 5px; font-family: monospace; font-size: 14px; }");
        htmlContent.append(".copy-btn { width: 100%; padding: 10px; background-color: #d4b344; color: white; border: none; border-radius: 5px; font-weight: bold; cursor: pointer; margin-top: 10px; }");
        htmlContent.append(".copy-btn:hover { background-color: #c0a040; }");
        htmlContent.append(".message { padding: 15px; margin: 10px 0; border-radius: 8px; text-align: center; font-weight: bold; }");
        htmlContent.append(".success { background-color: rgba(76, 175, 80, 0.2); color: #4CAF50; border: 1px solid #4CAF50; }");
        htmlContent.append(".error { background-color: rgba(231, 76, 60, 0.2); color: #e74c3c; border: 1px solid #e74c3c; }");
        htmlContent.append(".back-link { display: block; text-align: center; color: #d4b344; text-decoration: none; margin-top: 1rem; padding: 10px; background-color: #333394; border-radius: 8px; width: 100%; }");
        htmlContent.append(".back-link:hover { background-color: #242163; }");
        htmlContent.append(".lang-text { display: none; }");
        htmlContent.append(".lang-text.active { display: inline; }");
        htmlContent.append("@media screen and (max-width: 412px) { .stats-container { grid-template-columns: 1fr; } .language-selector { position: static; text-align: center; margin-bottom: 10px; } }");
        htmlContent.append("</style>");
        htmlContent.append("</head>");
        htmlContent.append("<body>");
        
        // Header with language selector
        htmlContent.append("<header class=\"header\">");
        htmlContent.append("<div class=\"language-selector\">");
        htmlContent.append("<button class=\"language-btn active\" onclick=\"switchLanguage('en')\">EN</button>");
        htmlContent.append("<button class=\"language-btn\" onclick=\"switchLanguage('am')\">አማ</button>");
        htmlContent.append("<button class=\"language-btn\" onclick=\"switchLanguage('zh')\">中文</button>");
        htmlContent.append("<button class=\"language-btn\" onclick=\"switchLanguage('es')\">ES</button>");
        htmlContent.append("</div>");
        htmlContent.append("<h1>👥 ");
        htmlContent.append("<span class=\"lang-text active\" data-lang=\"en\">Invite Friends</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"am\">ጓደኞችን ይጋብዙ</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"zh\">邀请朋友</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"es\">Invitar Amigos</span>");
        htmlContent.append("</h1>");
        htmlContent.append("<p>");
        htmlContent.append("<span class=\"lang-text active\" data-lang=\"en\">Earn 10% commission when friends make deposits</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"am\">ጓደኞች ተቀማጭ ገንዘብ ሲያደርጉ 10% ኮሚሽን ያግኙ</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"zh\">朋友存款时赚取10%佣金</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"es\">Gana 10% de comisión cuando los amigos hagan depósitos</span>");
        htmlContent.append("</p>");
        htmlContent.append("</header>");
        
        htmlContent.append("<div class=\"main-container\">");
        htmlContent.append("<div class=\"invite-container\">");

        // Add success/error messages if present
        if (success != null && !success.trim().isEmpty()) {
            htmlContent.append("<div class=\"message success\">").append(success.replace("+", " ")).append("</div>");
        }
        if (error != null && !error.trim().isEmpty()) {
            htmlContent.append("<div class=\"message error\">").append(error.replace("+", " ")).append("</div>");
        }

        // Stats section
        htmlContent.append("<div class=\"stats-container\">");
        htmlContent.append("<div class=\"stat-box\">");
        htmlContent.append("<div class=\"stat-value\">$").append(referralEarnings.toString()).append("</div>");
        htmlContent.append("<div class=\"stat-label\">");
        htmlContent.append("<span class=\"lang-text active\" data-lang=\"en\">Total Earned</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"am\">ጠቅላላ የተገኘ</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"zh\">总收入</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"es\">Total Ganado</span>");
        htmlContent.append("</div>");
        htmlContent.append("</div>");
        htmlContent.append("<div class=\"stat-box\">");
        htmlContent.append("<div class=\"stat-value\">").append(totalReferrals).append("</div>");
        htmlContent.append("<div class=\"stat-label\">");
        htmlContent.append("<span class=\"lang-text active\" data-lang=\"en\">Friends Invited</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"am\">የተጋበዙ ጓደኞች</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"zh\">已邀请朋友</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"es\">Amigos Invitados</span>");
        htmlContent.append("</div>");
        htmlContent.append("</div>");
        htmlContent.append("</div>");

        // Welcome message
        htmlContent.append("<div style=\"text-align: center; padding: 20px;\">");
        htmlContent.append("<h2>");
        htmlContent.append("<span class=\"lang-text active\" data-lang=\"en\">Welcome, ").append(userName).append("!</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"am\">እንኳን ደህና መጡ, ").append(userName).append("!</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"zh\">欢迎, ").append(userName).append("!</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"es\">¡Bienvenido, ").append(userName).append("!</span>");
        htmlContent.append("</h2>");
        htmlContent.append("</div>");

        // Referral link section
        htmlContent.append("<div class=\"referral-section\">");
        htmlContent.append("<div class=\"section-title\">");
        htmlContent.append("🔗 <span class=\"lang-text active\" data-lang=\"en\">Your Referral Link</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"am\">የእርስዎ የሪፈራል ሊንክ</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"zh\">您的推荐链接</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"es\">Tu Enlace de Referido</span>");
        htmlContent.append("</div>");
        
        htmlContent.append("<div class=\"referral-link\">");
        htmlContent.append("<input type=\"text\" class=\"link-input\" id=\"referralLink\" value=\"").append(referralLink).append("\" readonly>");
        htmlContent.append("<button class=\"copy-btn\" onclick=\"copyReferralLink()\">");
        htmlContent.append("📋 <span class=\"lang-text active\" data-lang=\"en\">Copy Link</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"am\">ሊንክ ቅዳ</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"zh\">复制链接</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"es\">Copiar Enlace</span>");
        htmlContent.append("</button>");
        htmlContent.append("</div>");
        
        htmlContent.append("<div style=\"font-size: 0.9em; line-height: 1.6; color: #ccc; margin-top: 15px;\">");
        htmlContent.append("<div class=\"lang-text active\" data-lang=\"en\">");
        htmlContent.append("<p><strong>Your Referral Code:</strong> ").append(referralCode).append("</p>");
        htmlContent.append("<p><strong>How it works:</strong></p>");
        htmlContent.append("<p>• Share your link with friends</p>");
        htmlContent.append("<p>• They sign up using your link</p>");
        htmlContent.append("<p>• When they make their first deposit, you earn 10% commission</p>");
        htmlContent.append("<p>• Commission is added to your wallet automatically</p>");
        htmlContent.append("</div>");
        
        htmlContent.append("<div class=\"lang-text\" data-lang=\"am\">");
        htmlContent.append("<p><strong>የእርስዎ የሪፈራል ኮድ:</strong> ").append(referralCode).append("</p>");
        htmlContent.append("<p><strong>እንዴት እንደሚሰራ:</strong></p>");
        htmlContent.append("<p>• ሊንክዎን ከጓደኞችዎ ጋር ያጋሩ</p>");
        htmlContent.append("<p>• ሊንክዎን በመጠቀም ይመዘገባሉ</p>");
        htmlContent.append("<p>• የመጀመሪያ ተቀማጭ ገንዘባቸውን ሲያደርጉ፣ 10% ኮሚሽን ያገኛሉ</p>");
        htmlContent.append("<p>• ኮሚሽን በራስ-ሰር ወደ ዋሌትዎ ይጨመራል</p>");
        htmlContent.append("</div>");
        
        htmlContent.append("<div class=\"lang-text\" data-lang=\"zh\">");
        htmlContent.append("<p><strong>您的推荐代码:</strong> ").append(referralCode).append("</p>");
        htmlContent.append("<p><strong>工作原理:</strong></p>");
        htmlContent.append("<p>• 与朋友分享您的链接</p>");
        htmlContent.append("<p>• 他们使用您的链接注册</p>");
        htmlContent.append("<p>• 当他们首次存款时，您将获得10%佣金</p>");
        htmlContent.append("<p>• 佣金会自动添加到您的钱包</p>");
        htmlContent.append("</div>");
        
        htmlContent.append("<div class=\"lang-text\" data-lang=\"es\">");
        htmlContent.append("<p><strong>Tu Código de Referido:</strong> ").append(referralCode).append("</p>");
        htmlContent.append("<p><strong>Cómo funciona:</strong></p>");
        htmlContent.append("<p>• Comparte tu enlace con amigos</p>");
        htmlContent.append("<p>• Se registran usando tu enlace</p>");
        htmlContent.append("<p>• Cuando hacen su primer depósito, ganas 10% de comisión</p>");
        htmlContent.append("<p>• La comisión se agrega automáticamente a tu billetera</p>");
        htmlContent.append("</div>");
        htmlContent.append("</div>");
        htmlContent.append("</div>");
        
        htmlContent.append("</div>");
        
        // Back link
        htmlContent.append("<a href=\"/dashboard\" class=\"back-link\">");
        htmlContent.append("← <span class=\"lang-text active\" data-lang=\"en\">Back to Dashboard</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"am\">ወደ ዳሽቦርድ ተመለስ</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"zh\">返回仪表板</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"es\">Volver al Panel</span>");
        htmlContent.append("</a>");
        htmlContent.append("</div>");

        // JavaScript for language switching and copy functionality
        htmlContent.append("<script>");
        htmlContent.append("let currentLang = 'en';");
        htmlContent.append("function switchLanguage(lang) {");
        htmlContent.append("  currentLang = lang;");
        htmlContent.append("  document.querySelectorAll('.lang-text').forEach(el => el.classList.remove('active'));");
        htmlContent.append("  document.querySelectorAll('.lang-text[data-lang=\"' + lang + '\"]').forEach(el => el.classList.add('active'));");
        htmlContent.append("  document.querySelectorAll('.language-btn').forEach(btn => btn.classList.remove('active'));");
        htmlContent.append("  event.target.classList.add('active');");
        htmlContent.append("  localStorage.setItem('selectedLanguage', lang);");
        htmlContent.append("}");
        htmlContent.append("function copyReferralLink() {");
        htmlContent.append("  const linkInput = document.getElementById('referralLink');");
        htmlContent.append("  linkInput.select();");
        htmlContent.append("  linkInput.setSelectionRange(0, 99999);");
        htmlContent.append("  document.execCommand('copy');");
        htmlContent.append("  const messages = {");
        htmlContent.append("    'en': 'Referral link copied to clipboard!',");
        htmlContent.append("    'am': 'የሪፈራል ሊንክ ወደ ክሊፕቦርድ ተቀድቷል!',");
        htmlContent.append("    'zh': '推荐链接已复制到剪贴板！',");
        htmlContent.append("    'es': '¡Enlace de referido copiado al portapapeles!'");
        htmlContent.append("  };");
        htmlContent.append("  alert(messages[currentLang] || messages['en']);");
        htmlContent.append("}");
        htmlContent.append("document.addEventListener('DOMContentLoaded', function() {");
        htmlContent.append("  const savedLang = localStorage.getItem('selectedLanguage') || 'en';");
        htmlContent.append("  if (savedLang !== 'en') {");
        htmlContent.append("    const langBtn = document.querySelector('.language-btn[onclick*=\"' + savedLang + '\"]');");
        htmlContent.append("    if (langBtn) langBtn.click();");
        htmlContent.append("  }");
        htmlContent.append("});");
        htmlContent.append("</script>");
        
        htmlContent.append("</body>");
        htmlContent.append("</html>");

        logger.info("Invite page generated successfully for user: " + authenticatedUserId);
        return Response.ok(htmlContent.toString(), "text/html").build();
    }
    
    @GET
    @Path("/income")
    public TemplateInstance incomePage(@CookieParam("user_id") String userId,
                                     @QueryParam("error") String error,
                                     @QueryParam("success") String success) {
        User user = getAuthenticatedUser(userId);
        
        try {
            // Get comprehensive income breakdown
            elonmusk.service.IncomeService.IncomeBreakdown breakdown = incomeService.getIncomeBreakdown(user.id);
            
            // Get available daily income to collect
            breakdown.availableDailyIncome = incomeService.getAvailableDailyIncome(user.id);
            
            // Get available gifts to collect
            List<elonmusk.model.DailyGift> availableGifts = giftService.getAvailableGifts(user.id);
            
            // Get total gift code earnings
            BigDecimal giftIncome = giftCodeService.getTotalGiftEarnings(user.id);
            
            return income.data("user", user)
                        .data("breakdown", breakdown)
                        .data("availableGifts", availableGifts)
                        .data("giftIncome", giftIncome)
                        .data("referralIncome", breakdown.referralEarnings)
                        .data("dailyIncome", breakdown.dailyIncomeCollected)
                        .data("totalIncome", breakdown.totalIncome)
                        .data("error", error)
                        .data("success", success);
        } catch (Exception e) {
            logger.severe("Error loading income page: " + e.getMessage());
            return income.data("user", user)
                        .data("breakdown", new elonmusk.service.IncomeService.IncomeBreakdown())
                        .data("availableGifts", new ArrayList<>())
                        .data("giftIncome", BigDecimal.ZERO)
                        .data("referralIncome", BigDecimal.ZERO)
                        .data("dailyIncome", BigDecimal.ZERO)
                        .data("totalIncome", BigDecimal.ZERO)
                        .data("error", "Failed to load income data")
                        .data("success", success);
        }
    }
    
    @POST
    @Path("/income/collect-all")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response collectAllDailyIncome(@CookieParam("user_id") String userId) {
        try {
            User user = getAuthenticatedUser(userId);
            
            // Get all available gifts
            List<elonmusk.model.DailyGift> availableGifts = giftService.getAvailableGifts(user.id);
            
            int collectedCount = 0;
            BigDecimal totalCollected = BigDecimal.ZERO;
            
            for (elonmusk.model.DailyGift gift : availableGifts) {
                try {
                    boolean collected = giftService.collectGift(user.id, gift.id);
                    if (collected) {
                        collectedCount++;
                        totalCollected = totalCollected.add(gift.giftAmount);
                    }
                } catch (Exception e) {
                    logger.warning("Failed to collect gift " + gift.id + ": " + e.getMessage());
                }
            }
            
            if (collectedCount > 0) {
                return Response.seeOther(java.net.URI.create("/income?success=Collected+" + collectedCount + "+gifts+totaling+$" + totalCollected)).build();
            } else {
                return Response.seeOther(java.net.URI.create("/income?error=No+gifts+available+to+collect")).build();
            }
            
        } catch (WebApplicationException e) {
            return Response.seeOther(java.net.URI.create("/login?returnUrl=/income&error=Please+login+to+collect+income")).build();
        } catch (Exception e) {
            logger.severe("Error collecting daily income: " + e.getMessage());
            return Response.seeOther(java.net.URI.create("/income?error=Failed+to+collect+income")).build();
        }
    }
    
    @GET
    @Path("/service")
    public TemplateInstance servicePage(@CookieParam("user_id") String userId,
                                      @QueryParam("success") String success,
                                      @QueryParam("error") String error) {
        User user = getAuthenticatedUser(userId);
        return service.data("user", user).data("success", success).data("error", error);
    }
    
    @GET
    @Path("/purchase")
    public Response purchasePage(@CookieParam("user_id") String userId,
                               @QueryParam("success") String success,
                               @QueryParam("error") String error) {
        logger.info("Purchase page accessed with userId: " + userId);
        
        // Check authentication first
        boolean isAuthenticated = false;
        String userName = "User";
        Long authenticatedUserId = null;
        
        if (userId != null && !userId.trim().isEmpty()) {
            try {
                Long userIdLong = Long.parseLong(userId.trim());
                if (userIdLong > 0) {
                    // Direct database check
                    try (java.sql.Connection conn = dataSource.getConnection();
                         java.sql.PreparedStatement stmt = conn.prepareStatement("SELECT id, name FROM users WHERE id = ?")) {
                        stmt.setLong(1, userIdLong);
                        try (java.sql.ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                isAuthenticated = true;
                                authenticatedUserId = rs.getLong("id");
                                userName = rs.getString("name");
                            }
                        }
                    } catch (Exception e) {
                        logger.warning("Database error during authentication: " + e.getMessage());
                    }
                }
            } catch (NumberFormatException e) {
                logger.warning("Invalid user ID format: " + userId);
            }
        }

        if (!isAuthenticated) {
            // Redirect to login
            return Response.seeOther(java.net.URI.create("/login?returnUrl=/purchase")).build();
        }

        // Get user's investment history from database
        java.util.List<java.util.Map<String, Object>> investments = new java.util.ArrayList<>();
        java.math.BigDecimal totalInvested = java.math.BigDecimal.ZERO;
        
        try {
            String sql = "SELECT ui.*, ip.image_url FROM user_investments ui " +
                        "LEFT JOIN investment_products ip ON ui.product_name = ip.name " +
                        "WHERE ui.user_id = ? ORDER BY ui.created_at DESC";
            
            try (java.sql.Connection conn = dataSource.getConnection();
                 java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, authenticatedUserId);
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        java.util.Map<String, Object> investment = new java.util.HashMap<>();
                        investment.put("id", rs.getLong("id"));
                        investment.put("productName", rs.getString("product_name"));
                        investment.put("investedAmount", rs.getBigDecimal("invested_amount"));
                        investment.put("dailyReturn", rs.getBigDecimal("daily_return"));
                        investment.put("totalReturn", rs.getBigDecimal("total_return"));
                        investment.put("startDate", rs.getDate("start_date"));
                        investment.put("endDate", rs.getDate("end_date"));
                        investment.put("status", rs.getString("status"));
                        investment.put("imageUrl", rs.getString("image_url"));
                        investments.add(investment);
                        
                        java.math.BigDecimal amount = rs.getBigDecimal("invested_amount");
                        if (amount != null) {
                            totalInvested = totalInvested.add(amount);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Error loading investment data: " + e.getMessage());
        }

        // Enhanced HTML response with multi-language support
        StringBuilder htmlContent = new StringBuilder();
        htmlContent.append("<!DOCTYPE html>");
        htmlContent.append("<html lang=\"en\">");
        htmlContent.append("<head>");
        htmlContent.append("<meta charset=\"UTF-8\">");
        htmlContent.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">");
        htmlContent.append("<title>Purchase History</title>");
        htmlContent.append("<style>");
        htmlContent.append("* { box-sizing: border-box; margin: 0; padding: 0; font-family: Arial, sans-serif; }");
        htmlContent.append("body { background: linear-gradient(to right, #3d82e9, #60b966); min-height: 100vh; display: flex; flex-direction: column; align-items: center; }");
        htmlContent.append(".header { color: white; padding: 1.5rem; text-align: center; width: 100%; background-color: #5f5fbc; box-shadow: 0 2px 5px rgba(0, 0, 0, 0.1); position: relative; }");
        htmlContent.append(".language-selector { position: absolute; top: 10px; right: 20px; }");
        htmlContent.append(".language-btn { background: rgba(255,255,255,0.2); color: white; border: 1px solid rgba(255,255,255,0.3); padding: 5px 10px; margin: 0 2px; border-radius: 15px; cursor: pointer; font-size: 12px; }");
        htmlContent.append(".language-btn.active { background: #d4b344; border-color: #d4b344; }");
        htmlContent.append(".main-container { padding: 2rem; display: flex; flex-direction: column; align-items: center; width: 100%; max-width: 900px; }");
        htmlContent.append(".purchase-container { background-color: #5858c1; padding: 20px; margin-top: 20px; color: #efedf5; border-radius: 10px; box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1); width: 100%; }");
        htmlContent.append(".stats-container { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 15px; margin-bottom: 20px; }");
        htmlContent.append(".stat-box { background-color: #242163; padding: 15px; border-radius: 8px; text-align: center; }");
        htmlContent.append(".stat-value { font-size: 1.5em; font-weight: bold; color: #d4b344; }");
        htmlContent.append(".stat-label { font-size: 0.9em; color: #ccc; margin-top: 5px; }");
        htmlContent.append(".investment-item { background-color: #333394; padding: 15px; margin-bottom: 15px; border-radius: 8px; border-left: 4px solid #d4b344; }");
        htmlContent.append(".investment-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }");
        htmlContent.append(".investment-name { font-size: 1.1em; font-weight: bold; color: #d4b344; }");
        htmlContent.append(".investment-status { padding: 4px 8px; border-radius: 12px; font-size: 0.8em; font-weight: bold; }");
        htmlContent.append(".status-active { background-color: #4CAF50; color: white; }");
        htmlContent.append(".status-completed { background-color: #2196F3; color: white; }");
        htmlContent.append(".investment-details { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; font-size: 0.9em; }");
        htmlContent.append(".detail-item { display: flex; justify-content: space-between; }");
        htmlContent.append(".detail-label { color: #ccc; }");
        htmlContent.append(".detail-value { color: white; font-weight: bold; }");
        htmlContent.append(".no-investments { text-align: center; padding: 40px; color: #ccc; }");
        htmlContent.append(".message { padding: 15px; margin: 10px 0; border-radius: 8px; text-align: center; font-weight: bold; }");
        htmlContent.append(".success { background-color: rgba(76, 175, 80, 0.2); color: #4CAF50; border: 1px solid #4CAF50; }");
        htmlContent.append(".error { background-color: rgba(231, 76, 60, 0.2); color: #e74c3c; border: 1px solid #e74c3c; }");
        htmlContent.append(".back-link { display: block; text-align: center; color: #d4b344; text-decoration: none; margin-top: 1rem; padding: 10px; background-color: #333394; border-radius: 8px; width: 100%; }");
        htmlContent.append(".back-link:hover { background-color: #242163; }");
        htmlContent.append(".lang-text { display: none; }");
        htmlContent.append(".lang-text.active { display: inline; }");
        htmlContent.append("@media screen and (max-width: 412px) { .stats-container { grid-template-columns: 1fr; } .investment-details { grid-template-columns: 1fr; } .language-selector { position: static; text-align: center; margin-bottom: 10px; } }");
        htmlContent.append("</style>");
        htmlContent.append("</head>");
        htmlContent.append("<body>");
        
        // Header with language selector
        htmlContent.append("<header class=\"header\">");
        htmlContent.append("<div class=\"language-selector\">");
        htmlContent.append("<button class=\"language-btn active\" onclick=\"switchLanguage('en')\">EN</button>");
        htmlContent.append("<button class=\"language-btn\" onclick=\"switchLanguage('am')\">አማ</button>");
        htmlContent.append("<button class=\"language-btn\" onclick=\"switchLanguage('zh')\">中文</button>");
        htmlContent.append("<button class=\"language-btn\" onclick=\"switchLanguage('es')\">ES</button>");
        htmlContent.append("</div>");
        htmlContent.append("<h1>🛒 ");
        htmlContent.append("<span class=\"lang-text active\" data-lang=\"en\">Purchase History</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"am\">የግዢ ታሪክ</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"zh\">购买历史</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"es\">Historial de Compras</span>");
        htmlContent.append("</h1>");
        htmlContent.append("<p>");
        htmlContent.append("<span class=\"lang-text active\" data-lang=\"en\">View your investment portfolio and returns</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"am\">የእርስዎን የኢንቨስትመንት ፖርትፎሊዮ እና ተመላሾች ይመልከቱ</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"zh\">查看您的投资组合和回报</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"es\">Ve tu cartera de inversiones y retornos</span>");
        htmlContent.append("</p>");
        htmlContent.append("</header>");
        
        htmlContent.append("<div class=\"main-container\">");
        htmlContent.append("<div class=\"purchase-container\">");

        // Add success/error messages if present
        if (success != null && !success.trim().isEmpty()) {
            htmlContent.append("<div class=\"message success\">").append(success.replace("+", " ")).append("</div>");
        }
        if (error != null && !error.trim().isEmpty()) {
            htmlContent.append("<div class=\"message error\">").append(error.replace("+", " ")).append("</div>");
        }

        // Stats section
        htmlContent.append("<div class=\"stats-container\">");
        htmlContent.append("<div class=\"stat-box\">");
        htmlContent.append("<div class=\"stat-value\">$").append(totalInvested.toString()).append("</div>");
        htmlContent.append("<div class=\"stat-label\">");
        htmlContent.append("<span class=\"lang-text active\" data-lang=\"en\">Total Invested</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"am\">ጠቅላላ ኢንቨስትመንት</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"zh\">总投资</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"es\">Total Invertido</span>");
        htmlContent.append("</div>");
        htmlContent.append("</div>");
        htmlContent.append("<div class=\"stat-box\">");
        htmlContent.append("<div class=\"stat-value\">").append(investments.size()).append("</div>");
        htmlContent.append("<div class=\"stat-label\">");
        htmlContent.append("<span class=\"lang-text active\" data-lang=\"en\">Active Investments</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"am\">ንቁ ኢንቨስትመንቶች</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"zh\">活跃投资</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"es\">Inversiones Activas</span>");
        htmlContent.append("</div>");
        htmlContent.append("</div>");
        
        // Calculate total returns
        java.math.BigDecimal totalReturns = java.math.BigDecimal.ZERO;
        for (java.util.Map<String, Object> inv : investments) {
            java.math.BigDecimal returns = (java.math.BigDecimal) inv.get("totalReturn");
            if (returns != null) {
                totalReturns = totalReturns.add(returns);
            }
        }
        
        htmlContent.append("<div class=\"stat-box\">");
        htmlContent.append("<div class=\"stat-value\">$").append(totalReturns.toString()).append("</div>");
        htmlContent.append("<div class=\"stat-label\">");
        htmlContent.append("<span class=\"lang-text active\" data-lang=\"en\">Total Returns</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"am\">ጠቅላላ ተመላሾች</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"zh\">总回报</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"es\">Retornos Totales</span>");
        htmlContent.append("</div>");
        htmlContent.append("</div>");
        htmlContent.append("</div>");

        // Welcome message
        htmlContent.append("<div style=\"text-align: center; padding: 20px;\">");
        htmlContent.append("<h2>");
        htmlContent.append("<span class=\"lang-text active\" data-lang=\"en\">Welcome, ").append(userName).append("!</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"am\">እንኳን ደህና መጡ, ").append(userName).append("!</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"zh\">欢迎, ").append(userName).append("!</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"es\">¡Bienvenido, ").append(userName).append("!</span>");
        htmlContent.append("</h2>");
        htmlContent.append("</div>");

        // Investment history
        if (investments.isEmpty()) {
            htmlContent.append("<div class=\"no-investments\">");
            htmlContent.append("<h3>");
            htmlContent.append("<span class=\"lang-text active\" data-lang=\"en\">No Investments Yet</span>");
            htmlContent.append("<span class=\"lang-text\" data-lang=\"am\">እስካሁን ኢንቨስትመንት የለም</span>");
            htmlContent.append("<span class=\"lang-text\" data-lang=\"zh\">暂无投资</span>");
            htmlContent.append("<span class=\"lang-text\" data-lang=\"es\">Aún No Hay Inversiones</span>");
            htmlContent.append("</h3>");
            htmlContent.append("<p>");
            htmlContent.append("<span class=\"lang-text active\" data-lang=\"en\">Start investing to see your portfolio here!</span>");
            htmlContent.append("<span class=\"lang-text\" data-lang=\"am\">ፖርትፎሊዮዎን እዚህ ለማየት ኢንቨስት ማድረግ ይጀምሩ!</span>");
            htmlContent.append("<span class=\"lang-text\" data-lang=\"zh\">开始投资以在此查看您的投资组合！</span>");
            htmlContent.append("<span class=\"lang-text\" data-lang=\"es\">¡Comienza a invertir para ver tu cartera aquí!</span>");
            htmlContent.append("</p>");
            htmlContent.append("</div>");
        } else {
            for (java.util.Map<String, Object> investment : investments) {
                htmlContent.append("<div class=\"investment-item\">");
                htmlContent.append("<div class=\"investment-header\">");
                htmlContent.append("<div class=\"investment-name\">").append(investment.get("productName")).append("</div>");
                
                String status = (String) investment.get("status");
                String statusClass = "ACTIVE".equals(status) ? "status-active" : "status-completed";
                htmlContent.append("<div class=\"investment-status ").append(statusClass).append("\">").append(status).append("</div>");
                htmlContent.append("</div>");
                
                htmlContent.append("<div class=\"investment-details\">");
                htmlContent.append("<div class=\"detail-item\">");
                htmlContent.append("<span class=\"detail-label\">");
                htmlContent.append("<span class=\"lang-text active\" data-lang=\"en\">Invested:</span>");
                htmlContent.append("<span class=\"lang-text\" data-lang=\"am\">ኢንቨስት የተደረገ:</span>");
                htmlContent.append("<span class=\"lang-text\" data-lang=\"zh\">已投资:</span>");
                htmlContent.append("<span class=\"lang-text\" data-lang=\"es\">Invertido:</span>");
                htmlContent.append("</span>");
                htmlContent.append("<span class=\"detail-value\">$").append(investment.get("investedAmount")).append("</span>");
                htmlContent.append("</div>");
                
                htmlContent.append("<div class=\"detail-item\">");
                htmlContent.append("<span class=\"detail-label\">");
                htmlContent.append("<span class=\"lang-text active\" data-lang=\"en\">Daily Return:</span>");
                htmlContent.append("<span class=\"lang-text\" data-lang=\"am\">ዕለታዊ ተመላሽ:</span>");
                htmlContent.append("<span class=\"lang-text\" data-lang=\"zh\">日收益:</span>");
                htmlContent.append("<span class=\"lang-text\" data-lang=\"es\">Retorno Diario:</span>");
                htmlContent.append("</span>");
                htmlContent.append("<span class=\"detail-value\">$").append(investment.get("dailyReturn")).append("</span>");
                htmlContent.append("</div>");
                
                htmlContent.append("<div class=\"detail-item\">");
                htmlContent.append("<span class=\"detail-label\">");
                htmlContent.append("<span class=\"lang-text active\" data-lang=\"en\">Total Return:</span>");
                htmlContent.append("<span class=\"lang-text\" data-lang=\"am\">ጠቅላላ ተመላሽ:</span>");
                htmlContent.append("<span class=\"lang-text\" data-lang=\"zh\">总回报:</span>");
                htmlContent.append("<span class=\"lang-text\" data-lang=\"es\">Retorno Total:</span>");
                htmlContent.append("</span>");
                htmlContent.append("<span class=\"detail-value\">$").append(investment.get("totalReturn")).append("</span>");
                htmlContent.append("</div>");
                
                htmlContent.append("<div class=\"detail-item\">");
                htmlContent.append("<span class=\"detail-label\">");
                htmlContent.append("<span class=\"lang-text active\" data-lang=\"en\">Period:</span>");
                htmlContent.append("<span class=\"lang-text\" data-lang=\"am\">ጊዜ:</span>");
                htmlContent.append("<span class=\"lang-text\" data-lang=\"zh\">期间:</span>");
                htmlContent.append("<span class=\"lang-text\" data-lang=\"es\">Período:</span>");
                htmlContent.append("</span>");
                htmlContent.append("<span class=\"detail-value\">").append(investment.get("startDate")).append(" - ").append(investment.get("endDate")).append("</span>");
                htmlContent.append("</div>");
                htmlContent.append("</div>");
                htmlContent.append("</div>");
            }
        }
        
        htmlContent.append("</div>");
        
        // Back link
        htmlContent.append("<a href=\"/dashboard\" class=\"back-link\">");
        htmlContent.append("← <span class=\"lang-text active\" data-lang=\"en\">Back to Dashboard</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"am\">ወደ ዳሽቦርድ ተመለስ</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"zh\">返回仪表板</span>");
        htmlContent.append("<span class=\"lang-text\" data-lang=\"es\">Volver al Panel</span>");
        htmlContent.append("</a>");
        htmlContent.append("</div>");

        // JavaScript for language switching
        htmlContent.append("<script>");
        htmlContent.append("let currentLang = 'en';");
        htmlContent.append("function switchLanguage(lang) {");
        htmlContent.append("  currentLang = lang;");
        htmlContent.append("  document.querySelectorAll('.lang-text').forEach(el => el.classList.remove('active'));");
        htmlContent.append("  document.querySelectorAll('.lang-text[data-lang=\"' + lang + '\"]').forEach(el => el.classList.add('active'));");
        htmlContent.append("  document.querySelectorAll('.language-btn').forEach(btn => btn.classList.remove('active'));");
        htmlContent.append("  event.target.classList.add('active');");
        htmlContent.append("  localStorage.setItem('selectedLanguage', lang);");
        htmlContent.append("}");
        htmlContent.append("document.addEventListener('DOMContentLoaded', function() {");
        htmlContent.append("  const savedLang = localStorage.getItem('selectedLanguage') || 'en';");
        htmlContent.append("  if (savedLang !== 'en') {");
        htmlContent.append("    const langBtn = document.querySelector('.language-btn[onclick*=\"' + savedLang + '\"]');");
        htmlContent.append("    if (langBtn) langBtn.click();");
        htmlContent.append("  }");
        htmlContent.append("});");
        htmlContent.append("</script>");
        
        htmlContent.append("</body>");
        htmlContent.append("</html>");

        logger.info("Purchase page generated successfully for user: " + authenticatedUserId);
        return Response.ok(htmlContent.toString(), "text/html").build();
    }
}