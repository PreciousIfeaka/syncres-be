CREATE TABLE cv_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    session_id VARCHAR(128),
    original_filename VARCHAR(255) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    file_type VARCHAR(10) NOT NULL CHECK (file_type IN ('PDF', 'DOCX', 'DOC')),
    file_size_bytes BIGINT,
    extracted_text TEXT,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT cv_docs_owner_check CHECK (user_id IS NOT NULL OR session_id IS NOT NULL)
);

CREATE INDEX idx_cv_documents_user_id ON cv_documents(user_id);
CREATE INDEX idx_cv_documents_session_id ON cv_documents(session_id);
