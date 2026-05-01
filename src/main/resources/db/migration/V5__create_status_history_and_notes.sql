CREATE TABLE application_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL REFERENCES job_applications(id) ON DELETE CASCADE,
    from_status application_status,
    to_status application_status NOT NULL,
    note TEXT,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_status_history_application_id ON application_status_history(application_id, changed_at DESC);

CREATE TABLE application_notes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL REFERENCES job_applications(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    note_type VARCHAR(50) DEFAULT 'GENERAL' CHECK (note_type IN ('GENERAL', 'INTERVIEW_PREP', 'RECRUITER_CONTACT', 'SALARY', 'FOLLOW_UP')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notes_application_id ON application_notes(application_id, created_at DESC);
