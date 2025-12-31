-- Enhanced Referral Validation Database Schema Updates
-- Execute this script to add necessary columns for 99.99% accurate validation

-- Add status column to users table if not exists
ALTER TABLE users ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'ACTIVE';
ALTER TABLE users ADD COLUMN IF NOT EXISTS account_locked BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_ip VARCHAR(45);
ALTER TABLE users ADD COLUMN IF NOT EXISTS device_fingerprint VARCHAR(255);

-- Add indexes for performance
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
CREATE INDEX IF NOT EXISTS idx_users_referral_code ON users(referral_code);
CREATE INDEX IF NOT EXISTS idx_users_account_locked ON users(account_locked);

-- Add referral_acceptances table constraints if not exists
ALTER TABLE referral_acceptances ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE referral_acceptances ADD COLUMN IF NOT EXISTS processed_at TIMESTAMP;

-- Add unique constraint to prevent duplicate referrals
ALTER TABLE referral_acceptances ADD CONSTRAINT IF NOT EXISTS unique_user_referral 
    UNIQUE (referred_user_id);

-- Add indexes for referral_acceptances
CREATE INDEX IF NOT EXISTS idx_referral_acceptances_status ON referral_acceptances(status);
CREATE INDEX IF NOT EXISTS idx_referral_acceptances_referrer ON referral_acceptances(referrer_id);
CREATE INDEX IF NOT EXISTS idx_referral_acceptances_referred ON referral_acceptances(referred_user_id);
CREATE INDEX IF NOT EXISTS idx_referral_acceptances_created ON referral_acceptances(created_at);

-- Add referral_invitations table constraints
ALTER TABLE referral_invitations ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- Add unique constraint for referral invitations
ALTER TABLE referral_invitations ADD CONSTRAINT IF NOT EXISTS unique_referral_invitation 
    UNIQUE (referrer_id, referred_user_id);

-- Add indexes for referral_invitations
CREATE INDEX IF NOT EXISTS idx_referral_invitations_status ON referral_invitations(status);
CREATE INDEX IF NOT EXISTS idx_referral_invitations_referrer ON referral_invitations(referrer_id);
CREATE INDEX IF NOT EXISTS idx_referral_invitations_referred ON referral_invitations(referred_user_id);

-- Update existing records to have proper status
UPDATE users SET status = 'ACTIVE' WHERE status IS NULL;
UPDATE users SET account_locked = FALSE WHERE account_locked IS NULL;

-- Add check constraints
ALTER TABLE referral_acceptances ADD CONSTRAINT IF NOT EXISTS check_referral_status 
    CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED'));

ALTER TABLE users ADD CONSTRAINT IF NOT EXISTS check_user_status 
    CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'BANNED'));

-- Create audit table for referral activities
CREATE TABLE IF NOT EXISTS referral_audit_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    referrer_id BIGINT,
    action VARCHAR(50) NOT NULL,
    status VARCHAR(20),
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    details JSONB
);

CREATE INDEX IF NOT EXISTS idx_referral_audit_user ON referral_audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_referral_audit_referrer ON referral_audit_log(referrer_id);
CREATE INDEX IF NOT EXISTS idx_referral_audit_created ON referral_audit_log(created_at);

-- Add foreign key constraints for data integrity
ALTER TABLE referral_acceptances ADD CONSTRAINT IF NOT EXISTS fk_referral_acceptances_referrer 
    FOREIGN KEY (referrer_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE referral_acceptances ADD CONSTRAINT IF NOT EXISTS fk_referral_acceptances_referred 
    FOREIGN KEY (referred_user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE referral_invitations ADD CONSTRAINT IF NOT EXISTS fk_referral_invitations_referrer 
    FOREIGN KEY (referrer_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE referral_invitations ADD CONSTRAINT IF NOT EXISTS fk_referral_invitations_referred 
    FOREIGN KEY (referred_user_id) REFERENCES users(id) ON DELETE CASCADE;

-- Create function to automatically log referral activities
CREATE OR REPLACE FUNCTION log_referral_activity()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO referral_audit_log (user_id, referrer_id, action, status, details)
        VALUES (NEW.referred_user_id, NEW.referrer_id, 'REFERRAL_CREATED', NEW.status, 
                json_build_object('table', TG_TABLE_NAME, 'operation', TG_OP));
        RETURN NEW;
    ELSIF TG_OP = 'UPDATE' THEN
        IF OLD.status != NEW.status THEN
            INSERT INTO referral_audit_log (user_id, referrer_id, action, status, details)
            VALUES (NEW.referred_user_id, NEW.referrer_id, 'STATUS_CHANGED', NEW.status,
                    json_build_object('old_status', OLD.status, 'new_status', NEW.status, 'table', TG_TABLE_NAME));
        END IF;
        RETURN NEW;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Create triggers for audit logging
DROP TRIGGER IF EXISTS trigger_referral_acceptances_audit ON referral_acceptances;
CREATE TRIGGER trigger_referral_acceptances_audit
    AFTER INSERT OR UPDATE ON referral_acceptances
    FOR EACH ROW EXECUTE FUNCTION log_referral_activity();

DROP TRIGGER IF EXISTS trigger_referral_invitations_audit ON referral_invitations;
CREATE TRIGGER trigger_referral_invitations_audit
    AFTER INSERT OR UPDATE ON referral_invitations
    FOR EACH ROW EXECUTE FUNCTION log_referral_activity();

-- Add comments for documentation
COMMENT ON TABLE referral_audit_log IS 'Audit log for all referral-related activities';
COMMENT ON COLUMN users.status IS 'User account status: ACTIVE, INACTIVE, SUSPENDED, BANNED';
COMMENT ON COLUMN users.account_locked IS 'Whether the user account is locked due to suspicious activity';
COMMENT ON COLUMN users.last_login_ip IS 'Last known IP address of the user';
COMMENT ON COLUMN users.device_fingerprint IS 'Device fingerprint for fraud detection';

-- Create view for referral statistics
CREATE OR REPLACE VIEW referral_statistics AS
SELECT 
    u.id as user_id,
    u.name,
    u.referral_code,
    COALESCE(u.total_referrals, 0) as total_referrals,
    COUNT(ra.id) FILTER (WHERE ra.status = 'ACCEPTED') as accepted_referrals,
    COUNT(ra.id) FILTER (WHERE ra.status = 'PENDING') as pending_referrals,
    COUNT(ra.id) FILTER (WHERE ra.status = 'REJECTED') as rejected_referrals,
    COUNT(ra.id) FILTER (WHERE ra.status = 'EXPIRED') as expired_referrals,
    COALESCE(u.referral_earnings, 0) as total_earnings
FROM users u
LEFT JOIN referral_acceptances ra ON u.id = ra.referrer_id
WHERE u.status = 'ACTIVE'
GROUP BY u.id, u.name, u.referral_code, u.total_referrals, u.referral_earnings;

COMMENT ON VIEW referral_statistics IS 'Comprehensive referral statistics for all users';

-- Grant necessary permissions
GRANT SELECT, INSERT, UPDATE ON referral_audit_log TO PUBLIC;
GRANT SELECT ON referral_statistics TO PUBLIC;