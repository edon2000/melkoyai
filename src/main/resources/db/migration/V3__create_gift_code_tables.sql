-- Create gift code tables if they don't exist
-- This ensures the gift code functionality works properly

-- GIFT CODES TABLE (Admin Created Codes)
CREATE TABLE IF NOT EXISTS gift_codes (
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
CREATE TABLE IF NOT EXISTS gift_code_redemptions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    gift_code_id BIGINT NOT NULL REFERENCES gift_codes(id) ON DELETE CASCADE,
    code VARCHAR(8) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    redeemed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, gift_code_id)
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_gift_codes_code ON gift_codes(code);
CREATE INDEX IF NOT EXISTS idx_gift_codes_active ON gift_codes(is_active, expires_at);
CREATE INDEX IF NOT EXISTS idx_gift_redemptions_user ON gift_code_redemptions(user_id);

-- Add total_daily_income_collected column to users table if it doesn't exist
ALTER TABLE users ADD COLUMN IF NOT EXISTS total_daily_income_collected DECIMAL(15,2) DEFAULT 0.00;