package elonmusk.controller;

import elonmusk.model.User;
import elonmusk.service.UserService;
import elonmusk.service.ReferralInvitationService;
import elonmusk.filter.SecurityUtil;
import elonmusk.validation.InputValidator;
import elonmusk.exception.ValidationException;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.logging.Logger;

@Path("/referral")
@Produces(MediaType.APPLICATION_JSON)
public class ReferralController {
    
    private static final Logger LOGGER = Logger.getLogger(ReferralController.class.getName());
    
    @Inject
    UserService userService;
    
    @Inject
    ReferralInvitationService referralInvitationService;
    
    /**
     * Validate referral code before signup
     */
    @GET
    @Path("/validate/{referralCode}")
    public Response validateReferralCode(@PathParam("referralCode") String referralCode) {
        try {
            if (referralCode == null || referralCode.trim().isEmpty()) {
                return Response.status(400)
                    .entity("{\"valid\": false, \"message\": \"Referral code is required\"}")
                    .build();
            }
            
            String sanitizedCode = SecurityUtil.sanitizeInput(referralCode.trim());
            User referrer = userService.findByReferralCode(sanitizedCode);
            
            if (referrer == null) {
                return Response.ok()
                    .entity("{\"valid\": false, \"message\": \"Invalid referral code\"}")
                    .build();
            }
            
            return Response.ok()
                .entity("{\"valid\": true, \"message\": \"Valid referral code\", \"referrerName\": \"" + referrer.name + "\"}")
                .build();
                
        } catch (Exception e) {
            LOGGER.severe("Error validating referral code: " + e.getMessage());
            return Response.status(500)
                .entity("{\"valid\": false, \"message\": \"Validation failed\"}")
                .build();
        }
    }
    
    /**
     * Get referral statistics for a user
     */
    @GET
    @Path("/stats/{userId}")
    public Response getReferralStats(@PathParam("userId") Long userId) {
        try {
            if (userId == null || userId <= 0) {
                return Response.status(400)
                    .entity("{\"error\": \"Invalid user ID\"}")
                    .build();
            }
            
            User user = userService.findById(userId);
            if (user == null) {
                return Response.status(404)
                    .entity("{\"error\": \"User not found\"}")
                    .build();
            }
            
            String stats = String.format(
                "{\"totalReferrals\": %d, \"referralEarnings\": %.2f, \"referralCode\": \"%s\"}",
                user.totalReferrals != null ? user.totalReferrals : 0,
                user.referralEarnings != null ? user.referralEarnings.doubleValue() : 0.0,
                user.referralCode != null ? user.referralCode : ""
            );
            
            return Response.ok().entity(stats).build();
            
        } catch (Exception e) {
            LOGGER.severe("Error getting referral stats: " + e.getMessage());
            return Response.status(500)
                .entity("{\"error\": \"Failed to get referral statistics\"}")
                .build();
        }
    }
    
    /**
     * Check if user is eligible for referral bonus
     */
    @GET
    @Path("/bonus-eligible/{userId}")
    public Response checkBonusEligibility(@PathParam("userId") Long userId) {
        try {
            if (userId == null || userId <= 0) {
                return Response.status(400)
                    .entity("{\"eligible\": false, \"message\": \"Invalid user ID\"}")
                    .build();
            }
            
            User user = userService.findById(userId);
            if (user == null || user.referredBy == null) {
                return Response.ok()
                    .entity("{\"eligible\": false, \"message\": \"User not referred by anyone\"}")
                    .build();
            }
            
            boolean isFirstDeposit = referralInvitationService.isFirstDeposit(userId);
            boolean bonusAlreadyPaid = referralInvitationService.hasReferralBonusBeenPaid(user.referredBy, userId);
            
            if (!isFirstDeposit) {
                return Response.ok()
                    .entity("{\"eligible\": false, \"message\": \"User has already made deposits\"}")
                    .build();
            }
            
            if (bonusAlreadyPaid) {
                return Response.ok()
                    .entity("{\"eligible\": false, \"message\": \"Referral bonus already paid\"}")
                    .build();
            }
            
            return Response.ok()
                .entity("{\"eligible\": true, \"message\": \"User eligible for referral bonus on first deposit\"}")
                .build();
                
        } catch (Exception e) {
            LOGGER.severe("Error checking bonus eligibility: " + e.getMessage());
            return Response.status(500)
                .entity("{\"eligible\": false, \"message\": \"Eligibility check failed\"}")
                .build();
        }
    }
}