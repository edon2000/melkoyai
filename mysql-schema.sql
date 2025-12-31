-- COMPLETE ELONMUSK INVESTMENT PLATFORM DATABASE SCHEMA
-- MySQL Compatible - PythonAnywhere Optimized

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS gift_code_redemptions;
DROP TABLE IF EXISTS gift_codes;
DROP TABLE IF EXISTS purchase_history;
DROP TABLE IF EXISTS daily_gifts;
DROP TABLE IF EXISTS deposit_history;
DROP TABLE IF EXISTS referral_acceptances;
DROP TABLE IF EXISTS admin_logs;
DROP TABLE IF EXISTS security_logs;
DROP TABLE IF EXISTS audit_logs;
DROP TABLE IF EXISTS notifications;
DROP TABLE IF EXISTS help_messages;
DROP TABLE IF EXISTS referral_bonuses;
DROP TABLE IF EXISTS referral_invitations;
DROP TABLE IF EXISTS password_reset_requests;
DROP TABLE IF EXISTS withdrawal_requests;
DROP TABLE IF EXISTS deposit_requests;
DROP TABLE IF EXISTS transactions;
DROP TABLE IF EXISTS user_investments;
DROP TABLE IF EXISTS investment_products;
DROP TABLE IF EXISTS users;

SET FOREIGN_KEY_CHECKS = 1;

-- USERS TABLE
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    wallet_balance DECIMAL(15,2) DEFAULT 1.90,
    referral_code VARCHAR(20) UNIQUE,
    referred_by BIGINT,
    total_referrals INTEGER DEFAULT 0,
    referral_earnings DECIMAL(15,2) DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (referred_by) REFERENCES users(id) ON DELETE SET NULL
);

-- INVESTMENT PRODUCTS TABLE
CREATE TABLE investment_products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    price DECIMAL(15,2) NOT NULL,
    daily_return_rate DECIMAL(5,4) NOT NULL,
    duration_days INTEGER NOT NULL,
    risk_level VARCHAR(20),
    category VARCHAR(50),
    image_url VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- USER INVESTMENTS TABLE
CREATE TABLE user_investments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    amount_invested DECIMAL(15,2) NOT NULL,
    start_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    end_date TIMESTAMP,
    daily_return DECIMAL(15,2),
    total_return DECIMAL(15,2) DEFAULT 0.00,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES investment_products(id) ON DELETE CASCADE
);

-- TRANSACTIONS TABLE
CREATE TABLE transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    description TEXT,
    status VARCHAR(20) DEFAULT 'PENDING',
    transaction_id VARCHAR(100) UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- DEPOSIT REQUESTS TABLE
CREATE TABLE deposit_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    payment_method VARCHAR(50),
    transaction_id VARCHAR(100),
    status VARCHAR(20) DEFAULT 'PENDING',
    admin_notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- WITHDRAWAL REQUESTS TABLE
CREATE TABLE withdrawal_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    bank_account VARCHAR(100),
    bank_name VARCHAR(100),
    account_holder VARCHAR(100),
    status VARCHAR(20) DEFAULT 'PENDING',
    admin_notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- PASSWORD RESET REQUESTS TABLE
CREATE TABLE password_reset_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    reset_token VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- REFERRAL INVITATIONS TABLE
CREATE TABLE referral_invitations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    referrer_id BIGINT NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    referral_code VARCHAR(20) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    accepted_at TIMESTAMP NULL,
    FOREIGN KEY (referrer_id) REFERENCES users(id) ON DELETE CASCADE
);

-- REFERRAL BONUSES TABLE
CREATE TABLE referral_bonuses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    referrer_id BIGINT NOT NULL,
    referred_id BIGINT NOT NULL,
    bonus_amount DECIMAL(15,2) NOT NULL,
    bonus_type VARCHAR(50),
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    paid_at TIMESTAMP NULL,
    FOREIGN KEY (referrer_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (referred_id) REFERENCES users(id) ON DELETE CASCADE
);

-- HELP MESSAGES TABLE
CREATE TABLE help_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    subject VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    status VARCHAR(20) DEFAULT 'OPEN',
    admin_response TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMP NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- NOTIFICATIONS TABLE
CREATE TABLE notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    type VARCHAR(50),
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- AUDIT LOGS TABLE
CREATE TABLE audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50),
    entity_id BIGINT,
    old_values JSON,
    new_values JSON,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

-- SECURITY LOGS TABLE
CREATE TABLE security_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    event_type VARCHAR(50) NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    success BOOLEAN DEFAULT TRUE,
    failure_reason VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

-- ADMIN LOGS TABLE
CREATE TABLE admin_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_username VARCHAR(100) NOT NULL,
    action VARCHAR(100) NOT NULL,
    target_user_id BIGINT,
    details TEXT,
    ip_address VARCHAR(45),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (target_user_id) REFERENCES users(id) ON DELETE SET NULL
);

-- REFERRAL ACCEPTANCES TABLE
CREATE TABLE referral_acceptances (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    referrer_id BIGINT NOT NULL,
    referred_user_id BIGINT NOT NULL,
    referral_code VARCHAR(20) NOT NULL,
    bonus_amount DECIMAL(15,2) DEFAULT 0.00,
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,
    FOREIGN KEY (referrer_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (referred_user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- DEPOSIT HISTORY TABLE
CREATE TABLE deposit_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    payment_method VARCHAR(50),
    transaction_reference VARCHAR(100),
    status VARCHAR(20) DEFAULT 'COMPLETED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- DAILY GIFTS TABLE
CREATE TABLE daily_gifts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    gift_amount DECIMAL(15,2) NOT NULL,
    gift_date DATE NOT NULL,
    claimed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    claimed_at TIMESTAMP NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY unique_user_date (user_id, gift_date)
);

-- PURCHASE HISTORY TABLE
CREATE TABLE purchase_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    purchase_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) DEFAULT 'COMPLETED',
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES investment_products(id) ON DELETE CASCADE
);

-- GIFT CODES TABLE
CREATE TABLE gift_codes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) UNIQUE NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    max_uses INTEGER DEFAULT 1,
    current_uses INTEGER DEFAULT 0,
    expires_at TIMESTAMP NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_by VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- GIFT CODE REDEMPTIONS TABLE
CREATE TABLE gift_code_redemptions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    gift_code_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    amount_received DECIMAL(15,2) NOT NULL,
    redeemed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (gift_code_id) REFERENCES gift_codes(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY unique_user_code (user_id, gift_code_id)
);

-- INSERT SAMPLE INVESTMENT PRODUCTS
INSERT INTO investment_products (name, description, price, daily_return_rate, duration_days, risk_level, category, image_url) VALUES
('STARTER', 'Perfect for beginners - Low risk, steady returns', 10.00, 0.0150, 7, 'LOW', 'BEGINNER', '/product/starter.jpg'),
('BASIC', 'Basic investment plan with moderate returns', 50.00, 0.0200, 14, 'LOW', 'BASIC', '/product/basic.jpg'),
('STANDARD', 'Standard plan for regular investors', 100.00, 0.0250, 21, 'MEDIUM', 'STANDARD', '/product/standard.jpg'),
('PREMIUM', 'Premium investment with higher returns', 500.00, 0.0300, 30, 'MEDIUM', 'PREMIUM', '/product/premium.jpg'),
('GOLD', 'Gold tier investment for serious investors', 1000.00, 0.0350, 45, 'HIGH', 'GOLD', '/product/gold.jpg'),
('PLATINUM', 'Platinum level with maximum returns', 5000.00, 0.0400, 60, 'HIGH', 'PLATINUM', '/product/platinum.jpg'),
('DIAMOND', 'Diamond tier - Ultimate investment plan', 10000.00, 0.0450, 90, 'VERY_HIGH', 'DIAMOND', '/product/diamond.jpg'),
('ELITE', 'Elite investment for high net worth individuals', 25000.00, 0.0500, 120, 'VERY_HIGH', 'ELITE', '/product/elite.jpg'),
('MASTER', 'Master level investment plan', 50000.00, 0.0550, 180, 'VERY_HIGH', 'MASTER', '/product/master.jpg'),
('LEGENDARY', 'Legendary tier - Exclusive investment opportunity', 100000.00, 0.0600, 365, 'VERY_HIGH', 'LEGENDARY', '/product/legendary.jpg'),
('ULTIMATE', 'Ultimate investment plan with supreme returns', 250000.00, 0.0650, 365, 'VERY_HIGH', 'ULTIMATE', '/product/ultimate.jpg');

-- CREATE INDEXES FOR PERFORMANCE
CREATE INDEX idx_users_phone ON users(phone_number);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_referral_code ON users(referral_code);
CREATE INDEX idx_transactions_user_id ON transactions(user_id);
CREATE INDEX idx_transactions_type ON transactions(transaction_type);
CREATE INDEX idx_user_investments_user_id ON user_investments(user_id);
CREATE INDEX idx_user_investments_status ON user_investments(status);
CREATE INDEX idx_deposit_requests_user_id ON deposit_requests(user_id);
CREATE INDEX idx_withdrawal_requests_user_id ON withdrawal_requests(user_id);
CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_security_logs_user_id ON security_logs(user_id);
CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_gift_codes_code ON gift_codes(code);
CREATE INDEX idx_gift_code_redemptions_user_id ON gift_code_redemptions(user_id);