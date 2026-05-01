CREATE TABLE match_job_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    jobrunr_job_id VARCHAR(255) NOT NULL,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    session_id VARCHAR(128),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    result_json TEXT,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_match_job_results_jobrunr_id ON match_job_results(jobrunr_job_id);
CREATE INDEX idx_match_job_results_session_id ON match_job_results(session_id);
