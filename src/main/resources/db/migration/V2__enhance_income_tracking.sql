-- Enhanced Income Tracking Migration
-- Add field to track total daily income collected for better performance

-- Add daily income tracking field to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS total_daily_income_collected DECIMAL(15,2) DEFAULT 0.00;

-- Add performance indexes for income queries
CREATE INDEX IF NOT EXISTS idx_daily_gifts_user_collected ON daily_gifts(user_id, is_collected);
CREATE INDEX IF NOT EXISTS idx_daily_gifts_date_collected ON daily_gifts(gift_date, is_collected);
CREATE INDEX IF NOT EXISTS idx_transactions_user_type ON transactions(user_id, type);
CREATE INDEX IF NOT EXISTS idx_transactions_created_at ON transactions(created_at);
CREATE INDEX IF NOT EXISTS idx_users_income_fields ON users(referral_earnings, total_daily_income_collected);

-- Add index for gift code redemptions
CREATE INDEX IF NOT EXISTS idx_gift_code_redemptions_user ON gift_code_redemptions(user_id);

-- Add index for referral acceptances
CREATE INDEX IF NOT EXISTS idx_referral_acceptances_user_status ON referral_acceptances(referred_user_id, status);

-- Update existing collected daily gifts to populate the new field
UPDATE users 
SET total_daily_income_collected = (
    SELECT COALESCE(SUM(gift_amount), 0) 
    FROM daily_gifts 
    WHERE daily_gifts.user_id = users.id 
    AND is_collected = true
)
WHERE EXISTS (
    SELECT 1 FROM daily_gifts 
    WHERE daily_gifts.user_id = users.id 
    AND is_collected = true
);

-- Add comment for documentation
COMMENT ON COLUMN users.total_daily_income_collected IS 'Aggregated total of all daily income collected by user for performance optimization';