-- V1__initial_schema.sql
-- Flyway migration for initial database setup

CREATE TABLE IF NOT EXISTS incidents (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(50) NOT NULL DEFAULT 'MEDIUM',
    category VARCHAR(50) NOT NULL,
    created_by BIGINT,
    assigned_to BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    due_date TIMESTAMP,
    resolved_at TIMESTAMP,
    root_cause TEXT,
    resolution_steps TEXT,
    resolution_summary TEXT,
    actual_resolution_minutes INTEGER,
    sla_missed BOOLEAN DEFAULT FALSE,
    tags VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS incident_comments (
    id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL REFERENCES incidents(id),
    user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS incident_timeline (
    id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL REFERENCES incidents(id),
    event_type VARCHAR(50) NOT NULL,
    description TEXT,
    performed_by BIGINT,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    action VARCHAR(100) NOT NULL,
    entity_name VARCHAR(255) NOT NULL,
    entity_id BIGINT,
    performed_by BIGINT,
    details TEXT,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Gamification Tables
CREATE TABLE IF NOT EXISTS employee_performance (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    username VARCHAR(100),
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    lifetime_points INTEGER DEFAULT 0,
    monthly_points INTEGER DEFAULT 0,
    current_rank VARCHAR(50) DEFAULT 'BEGINNER',
    total_incidents_resolved INTEGER DEFAULT 0,
    monthly_resolved_count INTEGER DEFAULT 0,
    sla_met_count INTEGER DEFAULT 0,
    sla_missed_count INTEGER DEFAULT 0,
    sla_success_rate DECIMAL(5,2) DEFAULT 0.0,
    total_resolution_time_minutes INTEGER DEFAULT 0,
    avg_resolution_time_minutes DECIMAL(10,2) DEFAULT 0.0,
    total_achievements INTEGER DEFAULT 0,
    rank_progress INTEGER DEFAULT 0,
    points_to_next_rank INTEGER DEFAULT 200,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_emp_perf_user_id ON employee_performance(user_id);
CREATE INDEX IF NOT EXISTS idx_emp_perf_lifetime_points ON employee_performance(lifetime_points DESC);
CREATE INDEX IF NOT EXISTS idx_emp_perf_monthly_points ON employee_performance(monthly_points DESC);
CREATE INDEX IF NOT EXISTS idx_emp_perf_current_rank ON employee_performance(current_rank);
