-- COMPLETE ELONMUSK INVESTMENT PLATFORM DATABASE SCHEMA
-- PostgreSQL Compatible - 100% Project Validated

DROP TABLE IF EXISTS gift_code_redemptions CASCADE;
DROP TABLE IF EXISTS gift_codes CASCADE;
DROP TABLE IF EXISTS purchase_history CASCADE;
DROP TABLE IF EXISTS daily_gifts CASCADE;
DROP TABLE IF EXISTS deposit_history CASCADE;
DROP TABLE IF EXISTS referral_acceptances CASCADE;
DROP TABLE IF EXISTS admin_logs CASCADE;
DROP TABLE IF EXISTS security_logs CASCADE;
DROP TABLE IF EXISTS audit_logs CASCADE;
DROP TABLE IF EXISTS notifications CASCADE;
DROP TABLE IF EXISTS help_messages CASCADE;
DROP TABLE IF EXISTS referral_bonuses CASCADE;
DROP TABLE IF EXISTS referral_invitations CASCADE;
DROP TABLE IF EXISTS password_reset_requests CASCADE;
DROP TABLE IF EXISTS withdrawal_requests CASCADE;
DROP TABLE IF EXISTS deposit_requests CASCADE;
DROP TABLE IF EXISTS transactions CASCADE;
DROP TABLE IF EXISTS user_investments CASCADE;
DROP TABLE IF EXISTS investment_products CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- USERS TABLE
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    wallet_balance DECIMAL(15,2) DEFAULT 1.90,
    referral_code VARCHAR(20) UNIQUE,
    referred_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    total_referrals INTEGER DEFAULT 0,
    referral_earnings DECIMAL(15,2) DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- INVESTMENT PRODUCTS TABLE
CREATE TABLE investment_products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    price DECIMAL(15,2) NOT NULL,
    daily_return_rate DECIMAL(5,4) NOT NULL,
    duration_days INTEGER NOT NULL,
    risk_level VARCHAR(20),
    category VARCHAR(50),
    image_url VARCHAR(500),
    is_active BOOLEAN DEFAULT true,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- USER INVESTMENTS TABLE
CREATE TABLE user_investments (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES investment_products(id) ON DELETE CASCADE,
    product_name VARCHAR(100) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    invested_amount DECIMAL(15,2) NOT NULL,
    daily_return DECIMAL(15,2) NOT NULL,
    total_return DECIMAL(15,2) DEFAULT 0.00,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- DEPOSIT REQUESTS TABLE
CREATE TABLE deposit_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    transaction_id VARCHAR(50) UNIQUE NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    admin_notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP
);

-- WITHDRAWAL REQUESTS TABLE
CREATE TABLE withdrawal_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount DECIMAL(15,2) NOT NULL,
    bank_account VARCHAR(100) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    admin_notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP
);

-- TRANSACTIONS TABLE
CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    category VARCHAR(50) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    net_amount DECIMAL(15,2) NOT NULL,
    description TEXT,
    related_user_id BIGINT REFERENCES users(id),
    status VARCHAR(20) DEFAULT 'COMPLETED',
    processed_by VARCHAR(50),
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- REFERRAL INVITATIONS TABLE
CREATE TABLE referral_invitations (
    id BIGSERIAL PRIMARY KEY,
    referrer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    referred_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    referral_code VARCHAR(20) NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (referrer_id, referred_user_id)
);

-- REFERRAL BONUSES TABLE
CREATE TABLE referral_bonuses (
    id BIGSERIAL PRIMARY KEY,
    referrer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    referred_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    bonus_amount DECIMAL(15,2) NOT NULL,
    deposit_amount DECIMAL(15,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (referrer_id, referred_user_id)
);

-- REFERRAL ACCEPTANCES TABLE
CREATE TABLE referral_acceptances (
    id BIGSERIAL PRIMARY KEY,
    referred_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    referrer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    referral_code VARCHAR(20) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    UNIQUE (referred_user_id, referrer_id)
);

-- PASSWORD RESET REQUESTS TABLE
CREATE TABLE password_reset_requests (
    id BIGSERIAL PRIMARY KEY,
    phone_number VARCHAR(20) NOT NULL,
    new_password VARCHAR(255) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    admin_notes TEXT,
    expires_at TIMESTAMP,
    processed_by VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP
);

-- HELP MESSAGES TABLE
CREATE TABLE help_messages (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subject VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    admin_reply TEXT,
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    replied_at TIMESTAMP
);

-- NOTIFICATIONS TABLE
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    type VARCHAR(30) NOT NULL,
    is_read BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- AUDIT LOGS TABLE
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(100) NOT NULL,
    details TEXT,
    ip_address INET,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- SECURITY LOGS TABLE
CREATE TABLE security_logs (
    id BIGSERIAL PRIMARY KEY,
    event VARCHAR(100) NOT NULL,
    details TEXT,
    ip_address INET,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ADMIN LOGS TABLE
CREATE TABLE admin_logs (
    id BIGSERIAL PRIMARY KEY,
    admin_user VARCHAR(50) NOT NULL,
    action VARCHAR(100) NOT NULL,
    details TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- DEPOSIT HISTORY TABLE
CREATE TABLE deposit_history (
    id BIGSERIAL PRIMARY KEY,
    deposit_request_id BIGINT NOT NULL REFERENCES deposit_requests(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount DECIMAL(15,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- DAILY GIFTS TABLE
CREATE TABLE daily_gifts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    gift_amount DECIMAL(15,2) NOT NULL,
    gift_date DATE NOT NULL,
    is_collected BOOLEAN DEFAULT false,
    collected_at TIMESTAMP,
    source VARCHAR(50) DEFAULT 'DAILY_INCOME',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, gift_date)
);

-- GIFT CODES TABLE (Admin Created Codes)
CREATE TABLE gift_codes (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(8) UNIQUE NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    max_uses INTEGER DEFAULT 1000,
    current_uses INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    created_by VARCHAR(50) DEFAULT 'ADMIN',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- GIFT CODE REDEMPTIONS TABLE
CREATE TABLE gift_code_redemptions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    gift_code_id BIGINT NOT NULL REFERENCES gift_codes(id) ON DELETE CASCADE,
    code VARCHAR(8) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    redeemed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, gift_code_id)
);

-- PURCHASE HISTORY TABLE
CREATE TABLE purchase_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id BIGINT REFERENCES investment_products(id) ON DELETE SET NULL,
    product_name VARCHAR(100) NOT NULL,
    product_image VARCHAR(500),
    amount DECIMAL(15,2) NOT NULL,
    purchase_type VARCHAR(50) DEFAULT 'INVESTMENT',
    status VARCHAR(20) DEFAULT 'COMPLETED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- PERFORMANCE INDEXES
CREATE INDEX idx_users_phone ON users(phone_number);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_referral_code ON users(referral_code);
CREATE INDEX idx_deposit_requests_user_id ON deposit_requests(user_id);
CREATE INDEX idx_deposit_requests_status ON deposit_requests(status);
CREATE INDEX idx_transactions_user_id ON transactions(user_id);
CREATE INDEX idx_referral_invitations_referrer ON referral_invitations(referrer_id);
CREATE INDEX idx_referral_acceptances_user ON referral_acceptances(referred_user_id);
CREATE INDEX idx_daily_gifts_user_date ON daily_gifts(user_id, gift_date);
CREATE INDEX idx_daily_gifts_collected ON daily_gifts(is_collected);
CREATE INDEX idx_purchase_history_user ON purchase_history(user_id);
CREATE INDEX idx_purchase_history_date ON purchase_history(created_at);
CREATE INDEX idx_gift_codes_code ON gift_codes(code);
CREATE INDEX idx_gift_codes_active ON gift_codes(is_active, expires_at);
CREATE INDEX idx_gift_redemptions_user ON gift_code_redemptions(user_id);

-- INSERT INVESTMENT PRODUCTS DATA
INSERT INTO investment_products (name, description, price, daily_return_rate, duration_days, risk_level, category, image_url, is_active) VALUES
('Starter Package', 'Perfect for beginners', 3.50, 0.025, 30, 'LOW', 'BEGINNER', '/product/starter.jpg', true),
('Basic Package', 'Build your foundation', 5.00, 0.030, 45, 'LOW', 'STANDARD', '/product/basic.jpg', true),
('Standard Package', 'Steady growth option', 6.00, 0.035, 60, 'MEDIUM', 'STANDARD', '/product/standard.jpg', true),
('Premium Package', 'Enhanced returns', 10.00, 0.040, 90, 'MEDIUM', 'PREMIUM', '/product/premium.jpg', true),
('Gold Package', 'Premium investment', 25.00, 0.045, 120, 'MEDIUM', 'PREMIUM', '/product/gold.jpg', true),
('Platinum Package', 'High-yield investment', 100.00, 0.050, 180, 'HIGH', 'VIP', '/product/platinum.jpg', true),
('Diamond Package', 'Elite investment tier', 200.00, 0.055, 240, 'HIGH', 'VIP', '/product/diamond.jpg', true),
('Elite Package', 'Professional grade', 500.00, 0.060, 300, 'HIGH', 'ELITE', '/product/elite.jpg', true),
('Master Package', 'Master investor level', 1000.00, 0.065, 365, 'HIGH', 'ELITE', '/product/master.jpg', true),
('Ultimate Package', 'Ultimate returns', 5000.00, 0.070, 450, 'VERY_HIGH', 'LEGENDARY', '/product/ultimate.jpg', true),
('Supreme Package', 'Supreme investment', 10000.00, 0.075, 540, 'VERY_HIGH', 'LEGENDARY', '/product/supreme.jpg', true),
('Legendary Package', 'Legendary status', 25000.00, 0.080, 720, 'VERY_HIGH', 'LEGENDARY', '/product/legendary.jpg', true)
ON CONFLICT (name) DO NOTHING;