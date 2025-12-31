package elonmusk.service;

import elonmusk.model.User;
import elonmusk.exception.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.regex.Pattern;

@ApplicationScoped
public class InvitationValidationService {
    
    private static final Logger LOGGER = Logger.getLogger(InvitationValidationService.class.getName());
    
    // Referral code format: REF + 4 digits from phone + 4 random digits (e.g., REF12345678)
    private static final Pattern REFERRAL_CODE_PATTERN = Pattern.compile("^REF[0-9]{8}$");
    
    // Invitation links expire after 30 days
    private static final int INVITATION_EXPIRY_DAYS = 30;
    
    @Inject
    DataSource dataSource;
    
    @Inject
    UserService userService;
    
    /**
     * Comprehensive invitation validation with proper error messages
     */
    public InvitationValidationResult validateInvitation(String referralCode, User newUser, String language) {
        // Default to English if language not provided
        if (language == null || language.trim().isEmpty()) {
            language = "en";
        }
        
        try {
            // 1. Basic input validation
            if (referralCode == null || referralCode.trim().isEmpty()) {
                return new InvitationValidationResult(false, getErrorMessage("EMPTY_CODE", language));
            }
            
            if (newUser == null || newUser.id == null) {
                return new InvitationValidationResult(false, getErrorMessage("INVALID_USER", language));
            }
            
            // 2. Format validation
            referralCode = referralCode.trim().toUpperCase();
            if (!REFERRAL_CODE_PATTERN.matcher(referralCode).matches()) {
                return new InvitationValidationResult(false, getErrorMessage("INVALID_FORMAT", language));
            }
            
            // 3. Check if referral code exists and get referrer
            User referrer = userService.findByReferralCode(referralCode);
            if (referrer == null) {
                return new InvitationValidationResult(false, getErrorMessage("CODE_NOT_FOUND", language));
            }
            
            // 4. Prevent self-referral
            if (referrer.id.equals(newUser.id)) {
                return new InvitationValidationResult(false, getErrorMessage("SELF_REFERRAL", language));
            }
            
            // 5. Check if user already has a referral (one-time use per user)
            if (hasExistingReferral(newUser.id)) {
                return new InvitationValidationResult(false, getErrorMessage("ALREADY_REFERRED", language));
            }
            
            // 6. Check if invitation has expired (time-based validation)
            if (isInvitationExpired(referralCode)) {
                return new InvitationValidationResult(false, getErrorMessage("LINK_EXPIRED", language));
            }
            
            // 7. Check if user has already accepted this specific referral
            if (hasAlreadyAcceptedReferral(newUser.id, referrer.id)) {
                return new InvitationValidationResult(false, getErrorMessage("ALREADY_ACCEPTED", language));
            }
            
            // All validations passed
            LOGGER.info("Invitation validation successful: User " + newUser.id + " can be referred by " + referrer.id);
            return new InvitationValidationResult(true, getSuccessMessage("VALID_INVITATION", language), referrer);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error validating invitation", e);
            return new InvitationValidationResult(false, getErrorMessage("SYSTEM_ERROR", language));
        }
    }
    
    /**
     * Check if user already has an existing referral
     */
    private boolean hasExistingReferral(Long userId) {
        String sql = "SELECT COUNT(*) FROM referral_acceptances WHERE referred_user_id = ? AND status IN ('PENDING', 'ACCEPTED')";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
            return false;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error checking existing referral", e);
            return true; // Fail safe - assume user already has referral
        }
    }
    
    /**
     * Check if invitation link has expired based on user creation time
     * Since referral codes don't contain timestamps, we check user creation date
     */
    private boolean isInvitationExpired(String referralCode) {
        // For the current format (REF + 8 digits), we check user creation date
        return checkUserCreationExpiry(referralCode);
    }
    
    /**
     * Fallback method to check expiry based on user creation date
     */
    private boolean checkUserCreationExpiry(String referralCode) {
        String sql = "SELECT created_at FROM users WHERE referral_code = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, referralCode);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    LocalDateTime userCreationTime = rs.getTimestamp("created_at").toLocalDateTime();
                    LocalDateTime expiryTime = userCreationTime.plusDays(INVITATION_EXPIRY_DAYS);
                    return LocalDateTime.now().isAfter(expiryTime);
                }
            }
            return true; // If user not found, consider expired
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error checking user creation expiry", e);
            return true; // Fail safe - assume expired
        }
    }
    
    /**
     * Check if user has already accepted this specific referral
     */
    private boolean hasAlreadyAcceptedReferral(Long userId, Long referrerId) {
        String sql = "SELECT COUNT(*) FROM referral_acceptances WHERE referred_user_id = ? AND referrer_id = ? AND status = 'ACCEPTED'";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, userId);
            stmt.setLong(2, referrerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
            return false;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error checking already accepted referral", e);
            return true; // Fail safe - assume already accepted
        }
    }
    
    /**
     * Get localized error messages
     */
    private String getErrorMessage(String errorCode, String language) {
        switch (errorCode) {
            case "EMPTY_CODE":
                switch (language) {
                    case "am": return "የግብዣ ኮድ ያስፈልጋል";
                    case "zh": return "需要邀请码";
                    case "es": return "Se requiere código de invitación";
                    default: return "Invitation code is required";
                }
            case "INVALID_FORMAT":
                switch (language) {
                    case "am": return "የግብዣ ኮድ ቅርጸት ትክክል አይደለም። ትክክለኛ ቅርጸት: REF12345678";
                    case "zh": return "邀请码格式不正确。正确格式：REF12345678";
                    case "es": return "Formato de código de invitación incorrecto. Formato correcto: REF12345678";
                    default: return "Invalid invitation code format. Correct format: REF12345678";
                }
            case "CODE_NOT_FOUND":
                switch (language) {
                    case "am": return "የግብዣ ኮድ አልተገኘም። እባክዎ ትክክለኛ ኮድ ያስገቡ";
                    case "zh": return "未找到邀请码。请输入有效的代码";
                    case "es": return "Código de invitación no encontrado. Por favor ingrese un código válido";
                    default: return "Invitation code not found. Please enter a valid code";
                }
            case "SELF_REFERRAL":
                switch (language) {
                    case "am": return "እራስዎን መጋበዝ አይችሉም";
                    case "zh": return "您不能邀请自己";
                    case "es": return "No puedes invitarte a ti mismo";
                    default: return "You cannot invite yourself";
                }
            case "ALREADY_REFERRED":
                switch (language) {
                    case "am": return "እርስዎ ቀደም ሲል በሌላ ሰው ተጋብዘዋል";
                    case "zh": return "您已经被其他人邀请过了";
                    case "es": return "Ya has sido invitado por otra persona";
                    default: return "You have already been invited by someone else";
                }
            case "LINK_EXPIRED":
                switch (language) {
                    case "am": return "የግብዣ አገናኝ ጊዜው አልፏል። እባክዎ አዲስ ግብዣ ይጠይቁ";
                    case "zh": return "邀请链接已过期。请申请新的邀请";
                    case "es": return "El enlace de invitación ha expirado. Por favor solicite una nueva invitación";
                    default: return "Invitation link has expired. Please request a new invitation";
                }
            case "ALREADY_ACCEPTED":
                switch (language) {
                    case "am": return "እርስዎ ይህንን ግብዣ ቀደም ሲል ተቀብለዋል";
                    case "zh": return "您已经接受过这个邀请";
                    case "es": return "Ya has aceptado esta invitación";
                    default: return "You have already accepted this invitation";
                }
            case "SYSTEM_ERROR":
                switch (language) {
                    case "am": return "የስርዓት ስህተት። እባክዎ ቆይተው ይሞክሩ";
                    case "zh": return "系统错误。请稍后再试";
                    case "es": return "Error del sistema. Por favor intente más tarde";
                    default: return "System error. Please try again later";
                }
            default:
                return "Unknown error occurred";
        }
    }
    
    /**
     * Get localized success messages
     */
    private String getSuccessMessage(String messageCode, String language) {
        switch (messageCode) {
            case "VALID_INVITATION":
                switch (language) {
                    case "am": return "የግብዣ ኮድ ትክክል ነው";
                    case "zh": return "邀请码有效";
                    case "es": return "Código de invitación válido";
                    default: return "Invitation code is valid";
                }
            default:
                return "Success";
        }
    }
    
    /**
     * Result class for invitation validation
     */
    public static class InvitationValidationResult {
        public final boolean isValid;
        public final String message;
        public final User referrer;
        
        public InvitationValidationResult(boolean isValid, String message) {
            this.isValid = isValid;
            this.message = message;
            this.referrer = null;
        }
        
        public InvitationValidationResult(boolean isValid, String message, User referrer) {
            this.isValid = isValid;
            this.message = message;
            this.referrer = referrer;
        }
    }
}