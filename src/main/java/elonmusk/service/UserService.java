package elonmusk.service;

import elonmusk.model.User;
import elonmusk.exception.ValidationException;
import elonmusk.exception.ServiceException;
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
public class UserService {
    
    private static final Logger LOGGER = Logger.getLogger(UserService.class.getName());
    
    @Inject
    io.agroal.api.AgroalDataSource dataSource;
    
    public User createUser(String name, String phoneNumber, String email, String password, String referralCode) {
        validateUserInput(name, phoneNumber, email, password);
        
        final String sql = "INSERT INTO users (name, phone_number, email, password, wallet_balance, referral_code, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            
            stmt.setString(1, name.trim());
            stmt.setString(2, phoneNumber.trim());
            stmt.setString(3, email.trim().toLowerCase());
            stmt.setString(4, password);
            stmt.setBigDecimal(5, new BigDecimal("1.90")); // Default $1.90 balance
            stmt.setString(6, referralCode);
            stmt.setTimestamp(7, java.sql.Timestamp.valueOf(LocalDateTime.now()));
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new ServiceException("Failed to create user, no rows affected");
            }
            
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return buildUser(keys.getLong(1), name, phoneNumber, email, password, referralCode);
                }
                throw new ServiceException("Failed to retrieve generated user ID");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error creating user: " + phoneNumber, e);
            throw new ServiceException("Failed to create user: " + e.getMessage(), e);
        }
    }
    
    public User findById(Long id) {
        if (id == null || id <= 0) {
            throw new ValidationException("Invalid user ID");
        }
        
        final String sql = "SELECT * FROM users WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? mapResultSet(rs) : null;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error finding user by ID: " + id, e);
            throw new ServiceException("Failed to find user: " + e.getMessage(), e);
        }
    }
    
    public List<User> findAll() {
        final String sql = "SELECT * FROM users ORDER BY created_at DESC";
        final List<User> users = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                users.add(mapResultSet(rs));
            }
            return users;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving all users", e);
            throw new ServiceException("Failed to retrieve users: " + e.getMessage(), e);
        }
    }
    
    private void validateUserInput(String name, String phoneNumber, String email, String password) {
        try {
            elonmusk.validation.InputValidator.validateName(name);
            elonmusk.validation.InputValidator.validatePhoneNumber(phoneNumber);
            elonmusk.validation.InputValidator.validateEmail(email);
            
            if (password == null || password.isEmpty()) {
                throw new ValidationException("Password is required");
            }
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Validation error", e);
            throw new ValidationException("Invalid input provided");
        }
    }
    
    private User buildUser(Long id, String name, String phoneNumber, String email, String password, String referralCode) {
        User user = new User();
        user.id = id;
        user.name = name.trim();
        user.phoneNumber = phoneNumber.trim();
        user.email = email.trim().toLowerCase();
        user.password = password;
        user.walletBalance = new BigDecimal("1.90"); // Default $1.90 balance
        user.referralCode = referralCode;
        user.createdAt = LocalDateTime.now();
        return user;
    }
    
    private User mapResultSet(ResultSet rs) throws SQLException {
        try {
            User user = new User();
            user.id = rs.getLong("id");
            user.name = rs.getString("name");
            user.phoneNumber = rs.getString("phone_number");
            user.email = rs.getString("email");
            user.password = rs.getString("password");
            user.walletBalance = rs.getBigDecimal("wallet_balance") != null ? rs.getBigDecimal("wallet_balance") : BigDecimal.ZERO;
            user.referralCode = rs.getString("referral_code");
            user.referredBy = rs.getObject("referred_by", Long.class);
            user.totalReferrals = rs.getObject("total_referrals", Integer.class) != null ? rs.getInt("total_referrals") : 0;
            user.referralEarnings = rs.getBigDecimal("referral_earnings") != null ? rs.getBigDecimal("referral_earnings") : BigDecimal.ZERO;
            user.createdAt = rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null;
            return user;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error mapping user result set", e);
            throw new ServiceException("Failed to map user data", e);
        }
    }
    
    public User findByPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            throw new ValidationException("Phone number is required");
        }
        
        try {
            elonmusk.validation.InputValidator.validatePhoneNumber(phoneNumber);
        } catch (Exception e) {
            throw new ValidationException("Invalid phone number format");
        }
        
        final String sql = "SELECT * FROM users WHERE phone_number = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, phoneNumber.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? mapResultSet(rs) : null;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error finding user by phone", e);
            throw new ServiceException("Failed to find user by phone: " + e.getMessage(), e);
        }
    }
    
    public User findByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new ValidationException("Email is required");
        }
        
        try {
            elonmusk.validation.InputValidator.validateEmail(email);
        } catch (Exception e) {
            throw new ValidationException("Invalid email format");
        }
        
        final String sql = "SELECT * FROM users WHERE email = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, email.trim().toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? mapResultSet(rs) : null;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error finding user by email", e);
            throw new ServiceException("Failed to find user by email: " + e.getMessage(), e);
        }
    }
    
    public User findByReferralCode(String referralCode) {
        if (referralCode == null || referralCode.trim().isEmpty()) {
            throw new ValidationException("Referral code is required");
        }
        
        final String sql = "SELECT * FROM users WHERE referral_code = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, referralCode.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? mapResultSet(rs) : null;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error finding user by referral code", e);
            throw new ServiceException("Failed to find user by referral code: " + e.getMessage(), e);
        }
    }
    
    public void updateWalletBalance(Long userId, BigDecimal newBalance) {
        if (userId == null || userId <= 0) {
            throw new ValidationException("Invalid user ID");
        }
        if (newBalance == null || newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Invalid balance amount");
        }
        
        final String sql = "UPDATE users SET wallet_balance = ?, updated_at = ? WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setBigDecimal(1, newBalance);
            stmt.setTimestamp(2, java.sql.Timestamp.valueOf(LocalDateTime.now()));
            stmt.setLong(3, userId);
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                LOGGER.warning("No user found with ID: " + userId + " for balance update");
                throw new ServiceException("User not found or balance not updated");
            }
            
            LOGGER.info("Wallet balance updated for user " + userId + ": " + newBalance + " ETB");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error updating wallet balance for user: " + userId, e);
            throw new ServiceException("Failed to update wallet balance: " + e.getMessage(), e);
        }
    }
    

    
    public boolean updateUserName(Long userId, String newName) {
        if (userId == null || userId <= 0) {
            throw new ValidationException("Invalid user ID");
        }
        if (newName == null || newName.trim().isEmpty()) {
            throw new ValidationException("Name is required");
        }
        
        try {
            elonmusk.validation.InputValidator.validateName(newName);
        } catch (Exception e) {
            throw new ValidationException("Invalid name format");
        }
        
        final String sql = "UPDATE users SET name = ?, updated_at = ? WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, newName.trim());
            stmt.setTimestamp(2, java.sql.Timestamp.valueOf(LocalDateTime.now()));
            stmt.setLong(3, userId);
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                LOGGER.info("Name updated successfully for user: " + userId);
                return true;
            } else {
                LOGGER.warning("No user found with ID: " + userId + " for name update");
                return false;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error updating name for user: " + userId, e);
            throw new ServiceException("Failed to update name: " + e.getMessage(), e);
        }
    }
    
    public boolean updateUserEmail(Long userId, String newEmail) {
        if (userId == null || userId <= 0) {
            throw new ValidationException("Invalid user ID");
        }
        if (newEmail == null || newEmail.trim().isEmpty()) {
            throw new ValidationException("Email is required");
        }
        
        try {
            elonmusk.validation.InputValidator.validateEmail(newEmail);
        } catch (Exception e) {
            throw new ValidationException("Invalid email format");
        }
        
        // Check if email already exists for another user
        User existingUser = findByEmail(newEmail);
        if (existingUser != null && !existingUser.id.equals(userId)) {
            throw new ValidationException("Email already exists for another user");
        }
        
        final String sql = "UPDATE users SET email = ?, updated_at = ? WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, newEmail.trim().toLowerCase());
            stmt.setTimestamp(2, java.sql.Timestamp.valueOf(LocalDateTime.now()));
            stmt.setLong(3, userId);
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                LOGGER.info("Email updated successfully for user: " + userId);
                return true;
            } else {
                LOGGER.warning("No user found with ID: " + userId + " for email update");
                return false;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error updating email for user: " + userId, e);
            throw new ServiceException("Failed to update email: " + e.getMessage(), e);
        }
    }
    
    public boolean deleteUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ValidationException("Invalid user ID");
        }
        
        // Verify user exists before deletion
        User user = findById(userId);
        if (user == null) {
            throw new ValidationException("User not found");
        }
        
        final String sql = "DELETE FROM users WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, userId);
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                LOGGER.info("User deleted successfully: " + userId + " (" + user.phoneNumber + ")");
                return true;
            } else {
                LOGGER.warning("No user found with ID: " + userId + " for deletion");
                return false;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error deleting user: " + userId, e);
            throw new ServiceException("Failed to delete user: " + e.getMessage(), e);
        }
    }
    
    public boolean updatePassword(Long userId, String newPassword) {
        if (userId == null || userId <= 0) {
            throw new ValidationException("Invalid user ID");
        }
        if (newPassword == null || newPassword.isEmpty()) {
            throw new ValidationException("New password is required");
        }
        
        try {
            elonmusk.validation.InputValidator.validatePassword(newPassword);
        } catch (Exception e) {
            throw new ValidationException("Invalid password format");
        }
        
        final String sql = "UPDATE users SET password = ?, updated_at = ? WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            String hashedPassword = elonmusk.filter.SecurityUtil.hashPassword(newPassword);
            stmt.setString(1, hashedPassword);
            stmt.setTimestamp(2, java.sql.Timestamp.valueOf(LocalDateTime.now()));
            stmt.setLong(3, userId);
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                LOGGER.info("Password updated successfully for user: " + userId);
                return true;
            } else {
                LOGGER.warning("No user found with ID: " + userId + " for password update");
                return false;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error updating password for user: " + userId, e);
            throw new ServiceException("Failed to update password: " + e.getMessage(), e);
        }
    }
}