package elonmusk.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.logging.Level;

@ApplicationScoped
public class User {
    
    private static final Logger LOGGER = Logger.getLogger(User.class.getName());
    
    @Inject
    public DataSource dataSource;
    
    public Long id;
    public String name;
    public String phoneNumber;
    public String email;
    public String password;
    public BigDecimal walletBalance = BigDecimal.ZERO;
    public String referralCode;
    public Long referredBy;
    public Integer totalReferrals = 0;
    public BigDecimal referralEarnings = BigDecimal.ZERO;
    public LocalDateTime createdAt = LocalDateTime.now();
    
    public User findByPhoneNumber(String phoneNumber) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users WHERE phone_number = ?")) {
            stmt.setString(1, phoneNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
                return null;
            }
        }
    }
    
    public User findByEmail(String email) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users WHERE email = ?")) {
            stmt.setString(1, email.toLowerCase().trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
                return null;
            }
        }
    }
    
    public User findById(Long id) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
                return null;
            }
        }
    }
    
    public void save() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DataSource is null - User model not properly injected");
        }
        try {
            if (id == null) {
                insert();
            } else {
                update();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error saving user", e);
            throw e;
        }
    }
    
    private void insert() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO users (name, phone_number, email, password, wallet_balance, referral_code, referred_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                 PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setString(2, phoneNumber);
            stmt.setString(3, email);
            stmt.setString(4, password);
            stmt.setBigDecimal(5, walletBalance);
            stmt.setString(6, referralCode);
            stmt.setObject(7, referredBy);
            stmt.setTimestamp(8, java.sql.Timestamp.valueOf(createdAt));
            stmt.executeUpdate();
            
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    id = keys.getLong(1);
                }
            }
        }
    }
    
    private void update() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE users SET name=?, phone_number=?, email=?, password=?, wallet_balance=?, referral_code=?, referred_by=? WHERE id=?")) {
            stmt.setString(1, name);
            stmt.setString(2, phoneNumber);
            stmt.setString(3, email);
            stmt.setString(4, password);
            stmt.setBigDecimal(5, walletBalance);
            stmt.setString(6, referralCode);
            stmt.setObject(7, referredBy);
            stmt.setLong(8, id);
            stmt.executeUpdate();
        }
    }
    
    private User mapResultSet(ResultSet rs) throws SQLException {
        User user = new User();
        user.id = rs.getLong("id");
        user.name = rs.getString("name");
        user.phoneNumber = rs.getString("phone_number");
        user.email = rs.getString("email");
        user.password = rs.getString("password");
        user.walletBalance = rs.getBigDecimal("wallet_balance");
        user.referralCode = rs.getString("referral_code");
        user.referredBy = rs.getObject("referred_by", Long.class);
        user.totalReferrals = rs.getInt("total_referrals");
        user.referralEarnings = rs.getBigDecimal("referral_earnings");
        user.createdAt = rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null;
        return user;
    }
    
    public User findByReferralCode(String referralCode) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users WHERE referral_code = ?")) {
            stmt.setString(1, referralCode);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
                return null;
            }
        }
    }
    
    public void updateWalletBalance(Long userId, BigDecimal newBalance) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE users SET wallet_balance = ? WHERE id = ?")) {
            stmt.setBigDecimal(1, newBalance);
            stmt.setLong(2, userId);
            stmt.executeUpdate();
        }
    }
    
    public List<User> findAll() throws SQLException {
        String sql = "SELECT * FROM users ORDER BY created_at DESC";
        List<User> users = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                users.add(mapResultSet(rs));
            }
        }
        return users;
    }

}