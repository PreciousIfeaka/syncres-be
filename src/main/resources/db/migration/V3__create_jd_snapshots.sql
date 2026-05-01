CREATE TABLE jd_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    session_id VARCHAR(128),
    source_url VARCHAR(2000),
    company_name VARCHAR(255),
    role_title VARCHAR(255),
    raw_text TEXT NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT jd_snapshots_owner_check CHECK (user_id IS NOT NULL OR session_id IS NOT NULL)
);

CREATE INDEX idx_jd_snapshots_user_id ON jd_snapshots(user_id);
CREATE INDEX idx_jd_snapshots_session_id ON jd_snapshots(session_id);
