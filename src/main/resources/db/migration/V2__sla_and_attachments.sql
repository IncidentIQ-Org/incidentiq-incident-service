CREATE TABLE IF NOT EXISTS sla_configs (
    id BIGSERIAL PRIMARY KEY,
    priority VARCHAR(50) NOT NULL UNIQUE,
    target_hours INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS incident_attachments (
    id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    data BYTEA NOT NULL,
    is_safe BOOLEAN DEFAULT FALSE,
    scan_result VARCHAR(255),
    uploaded_by BIGINT NOT NULL,
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attachment_incident FOREIGN KEY (incident_id) REFERENCES incidents(id) ON DELETE CASCADE
);
