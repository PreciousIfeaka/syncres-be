-- JobRunr PostgreSQL schema
CREATE TABLE jobrunr_jobs (
    id UUID PRIMARY KEY,
    version INTEGER NOT NULL,
    jobAsJson TEXT NOT NULL,
    jobSignature VARCHAR(512) NOT NULL,
    state VARCHAR(32) NOT NULL,
    createdAt TIMESTAMPTZ NOT NULL,
    updatedAt TIMESTAMPTZ NOT NULL,
    scheduledAt TIMESTAMPTZ,
    recurringJobId VARCHAR(128)
);

CREATE INDEX idx_jobrunr_jobs_state ON jobrunr_jobs (state);
CREATE INDEX idx_jobrunr_jobs_scheduledAt ON jobrunr_jobs (scheduledAt);

CREATE TABLE jobrunr_recurring_jobs (
    id VARCHAR(128) PRIMARY KEY,
    version INTEGER NOT NULL,
    jobAsJson TEXT NOT NULL,
    createdAt TIMESTAMPTZ NOT NULL,
    updatedAt TIMESTAMPTZ NOT NULL
);

CREATE TABLE jobrunr_background_job_servers (
    id UUID PRIMARY KEY,
    workerPoolSize INTEGER NOT NULL,
    pollIntervalInSeconds INTEGER NOT NULL,
    firstHeartbeat TIMESTAMPTZ NOT NULL,
    lastHeartbeat TIMESTAMPTZ NOT NULL,
    running BOOLEAN NOT NULL,
    systemWork BOOLEAN NOT NULL
);

CREATE TABLE jobrunr_metadata (
    id VARCHAR(128) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    owner VARCHAR(128) NOT NULL,
    value TEXT NOT NULL,
    createdAt TIMESTAMPTZ NOT NULL,
    updatedAt TIMESTAMPTZ NOT NULL
);
