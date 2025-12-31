package elonmusk.validation;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.regex.Pattern;

@ApplicationScoped
public class Validator {
    
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[97][0-9]{8}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    
    public String normalizePhone(String phone) {
        if (phone == null) return null;
        String cleaned = phone.replaceAll("[^0-9]", "");
        return cleaned.length() == 10 && cleaned.startsWith("0") ? cleaned.substring(1) : cleaned;
    }
    
    public void validatePhone(String phone) {
        if (phone == null || !PHONE_PATTERN.matcher(phone).matches()) {
            throw new IllegalArgumentException("Invalid Ethiopian phone number");
        }
    }
    
    public void validateEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email.toLowerCase().trim()).matches()) {
            throw new IllegalArgumentException("Invalid email format");
        }
    }
    
    public void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
    }
    
    public void validateName(String name) {
        if (name == null || name.trim().length() < 2) {
            throw new IllegalArgumentException("Name must be at least 2 characters");
        }
    }
}