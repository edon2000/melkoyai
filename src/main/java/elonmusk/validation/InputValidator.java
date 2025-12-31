package elonmusk.validation;

import elonmusk.exception.ValidationException;
import elonmusk.util.SecurityUtil;
import java.math.BigDecimal;
import java.util.regex.Pattern;
import java.util.logging.Logger;
import java.util.logging.Level;

public class InputValidator {
    
    private static final Logger LOGGER = Logger.getLogger(InputValidator.class.getName());
    
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(09|07)\\d{8}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z\\s]{2,50}$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^\\d{6}$");
    private static final Pattern TRANSACTION_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]{6,50}$");
    private static final Pattern REFERRAL_CODE_PATTERN = Pattern.compile("^REF[a-zA-Z0-9]{4,10}$");
    private static final Pattern BANK_ACCOUNT_PATTERN = Pattern.compile("^[0-9]{10,30}$");
    
    public static void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Name is required");
        }
        
        String trimmedName = name.trim();
        
        if (!SecurityUtil.isValidInput(trimmedName, 50)) {
            throw new ValidationException("Name contains invalid characters or is too long");
        }
        
        if (!NAME_PATTERN.matcher(trimmedName).matches()) {
            throw new ValidationException("Name must be 2-50 characters and contain only letters and spaces");
        }
        
        // Additional security checks
        if (trimmedName.length() < 2) {
            throw new ValidationException("Name must be at least 2 characters");
        }
        
        if (trimmedName.matches(".*\\d.*")) {
            throw new ValidationException("Name cannot contain numbers");
        }
    }
    
    public static void validatePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            throw new ValidationException("Phone number is required");
        }
        
        String trimmedPhone = phoneNumber.trim();
        
        if (!SecurityUtil.isValidInput(trimmedPhone, 15)) {
            throw new ValidationException("Phone number contains invalid characters");
        }
        
        if (!PHONE_PATTERN.matcher(trimmedPhone).matches()) {
            throw new ValidationException("Phone must start with 09 or 07 followed by 8 digits");
        }
        
        // Additional validation for Ethiopian phone numbers
        if (trimmedPhone.length() != 10) {
            throw new ValidationException("Phone number must be exactly 10 digits");
        }
    }
    
    public static void validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new ValidationException("Email is required");
        }
        
        String trimmedEmail = email.trim().toLowerCase();
        
        if (!SecurityUtil.isValidInput(trimmedEmail, 100)) {
            throw new ValidationException("Email contains invalid characters or is too long");
        }
        
        if (!EMAIL_PATTERN.matcher(trimmedEmail).matches()) {
            throw new ValidationException("Please enter a valid email address");
        }
        
        // Additional email validation
        if (trimmedEmail.length() > 100) {
            throw new ValidationException("Email address is too long");
        }
        
        if (trimmedEmail.contains("..") || trimmedEmail.startsWith(".") || trimmedEmail.endsWith(".")) {
            throw new ValidationException("Email format is invalid");
        }
    }
    
    public static void validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new ValidationException("Password is required");
        }
        
        // Use special password validation that accepts all characters
        if (!SecurityUtil.isValidPasswordInput(password, 100)) {
            throw new ValidationException("Password is too long");
        }
        
        // Only require minimum 6 characters - accept ANY characters
        if (password.length() < 6) {
            throw new ValidationException("Password must be at least 6 characters");
        }
        
        // Accept all passwords 6+ characters - letters, numbers, symbols, spaces, everything
    }
    
    public static void validateAmount(BigDecimal amount, BigDecimal minAmount, BigDecimal maxAmount) {
        if (amount == null) {
            throw new ValidationException("Amount is required");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Amount must be greater than zero");
        }
        if (minAmount != null && amount.compareTo(minAmount) < 0) {
            throw new ValidationException("Minimum amount is $" + minAmount);
        }
        if (maxAmount != null && amount.compareTo(maxAmount) > 0) {
            throw new ValidationException("Maximum amount is $" + maxAmount);
        }
    }
    
    public static void validateTransactionId(String transactionId) {
        if (transactionId == null || transactionId.trim().isEmpty()) {
            throw new ValidationException("Transaction ID is required");
        }
        
        String cleanId = transactionId.trim().toUpperCase();
        
        // Must be 8-12 characters
        if (cleanId.length() < 8 || cleanId.length() > 12) {
            throw new ValidationException("Transaction ID must be 8-12 characters (e.g., CCL76AXGT4)");
        }
        
        // Must contain both letters and numbers
        boolean hasLetter = false;
        boolean hasNumber = false;
        
        for (char c : cleanId.toCharArray()) {
            if (!Character.isLetterOrDigit(c)) {
                throw new ValidationException("Transaction ID can only contain letters and numbers");
            }
            if (Character.isLetter(c)) hasLetter = true;
            if (Character.isDigit(c)) hasNumber = true;
        }
        
        if (!hasLetter || !hasNumber) {
            throw new ValidationException("Transaction ID must contain both letters and numbers (e.g., CCL76AXGT4)");
        }
    }
    
    public static void validatePaymentMethod(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.trim().isEmpty()) {
            throw new ValidationException("Payment method is required");
        }
        
        String trimmedMethod = paymentMethod.trim().toLowerCase();
        
        if (!SecurityUtil.isValidInput(trimmedMethod, 20)) {
            throw new ValidationException("Payment method contains invalid characters");
        }
        
        if (!elonmusk.config.SecurityConfig.isValidPaymentMethod(trimmedMethod)) {
            throw new ValidationException("Invalid payment method. Only CBE and Telebirr are supported");
        }
    }
    
    public static void validateReferralCode(String referralCode) {
        if (referralCode == null || referralCode.trim().isEmpty()) {
            return; // Referral code is optional
        }
        
        String trimmedCode = referralCode.trim().toUpperCase();
        
        if (!SecurityUtil.isValidInput(trimmedCode, 20)) {
            throw new ValidationException("Referral code contains invalid characters");
        }
        
        if (!REFERRAL_CODE_PATTERN.matcher(trimmedCode).matches()) {
            throw new ValidationException("Invalid referral code format");
        }
    }
    
    public static void validateBankAccount(String bankAccount) {
        if (bankAccount == null || bankAccount.trim().isEmpty()) {
            throw new ValidationException("Bank account is required");
        }
        
        String trimmedAccount = bankAccount.trim();
        
        if (!SecurityUtil.isValidInput(trimmedAccount, 30)) {
            throw new ValidationException("Bank account contains invalid characters or is too long");
        }
        
        if (!BANK_ACCOUNT_PATTERN.matcher(trimmedAccount).matches()) {
            throw new ValidationException("Bank account must be 10-30 digits only");
        }
    }
    
    public static void validateStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            throw new ValidationException("Status is required");
        }
        
        String trimmedStatus = status.trim().toUpperCase();
        
        if (!elonmusk.config.SecurityConfig.isValidStatus(trimmedStatus)) {
            throw new ValidationException("Invalid status value");
        }
    }
}