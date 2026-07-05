-- Add password reset OTP columns
ALTER TABLE users ADD COLUMN IF NOT EXISTS otp_code VARCHAR(10);
ALTER TABLE users ADD COLUMN IF NOT EXISTS otp_expiry TIMESTAMP;

-- Add staff-specific profile columns
ALTER TABLE users ADD COLUMN IF NOT EXISTS designation VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS specialization VARCHAR(255);

-- Add comments author role column
ALTER TABLE comments ADD COLUMN IF NOT EXISTS author_role VARCHAR(100);

-- Track ticket creator username securely
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS created_by_name VARCHAR(255);

-- Enable user active/disabled status
ALTER TABLE users ADD COLUMN IF NOT EXISTS enabled BOOLEAN DEFAULT TRUE;
