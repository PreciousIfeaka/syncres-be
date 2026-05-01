CREATE TABLE otp_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    purpose VARCHAR(20) NOT NULL CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),
    expires_at TIMESTAMPTZ NOT NULL,
    used BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_otp_tokens_user_id ON otp_tokens(user_id, purpose);
