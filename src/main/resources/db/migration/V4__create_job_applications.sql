CREATE TYPE application_status AS ENUM (
    'SAVED', 'APPLIED', 'PHONE_SCREEN', 'INTERVIEW', 'FINAL_ROUND', 
    'OFFER', 'ACCEPTED', 'DECLINED', 'REJECTED', 'WITHDRAWN'
);

CREATE TABLE job_applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    cv_document_id UUID REFERENCES cv_documents(id) ON DELETE SET NULL,
    jd_snapshot_id UUID REFERENCES jd_snapshots(id) ON DELETE SET NULL,
    company_name VARCHAR(255) NOT NULL,
    role_title VARCHAR(255),
    application_status application_status NOT NULL DEFAULT 'SAVED',
    match_score SMALLINT CHECK (match_score >= 0 AND match_score <= 100),
    match_summary TEXT,
    matched_skills TEXT[],
    missing_skills TEXT[],
    retailored_cv_path VARCHAR(500),
    jd_url VARCHAR(2000),
    applied_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_job_applications_user_id ON job_applications(user_id);
CREATE INDEX idx_job_applications_status ON job_applications(user_id, application_status);
CREATE INDEX idx_job_applications_company ON job_applications(user_id, company_name);
CREATE INDEX idx_job_applications_created_at ON job_applications(user_id, created_at DESC);
