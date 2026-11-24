CREATE TABLE database_backups (
    id UUID PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    trigger_type VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    size_bytes BIGINT,
    error_message VARCHAR(1000),
    created_by UUID
);

CREATE INDEX idx_database_backups_started_at
    ON database_backups (started_at DESC);
