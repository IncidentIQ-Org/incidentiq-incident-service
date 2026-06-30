-- V4: Make SLA configuration a Priority x Complexity matrix.
-- The original table had a single-column UNIQUE(priority); we replace it with a
-- composite UNIQUE(priority, complexity) and seed the full 4x4 (16-cell) matrix.

-- 1. Add the complexity dimension (nullable for the backfill step).
ALTER TABLE sla_configs ADD COLUMN IF NOT EXISTS complexity VARCHAR(20);

-- 2. Drop the obsolete single-column unique constraint (Postgres auto-name).
ALTER TABLE sla_configs DROP CONSTRAINT IF EXISTS sla_configs_priority_key;

-- 3. Clear the old priority-only rows; they are pure configuration defaults.
DELETE FROM sla_configs;

-- 4. Seed the full matrix (target hours per spec). Critical+Easy fixed fast,
--    Critical+Complex gets reasonable investigation time, Low+Complex longest.
INSERT INTO sla_configs (priority, complexity, target_hours) VALUES
  ('CRITICAL','EASY',1),   ('CRITICAL','MEDIUM',2),  ('CRITICAL','HARD',4),  ('CRITICAL','COMPLEX',6),
  ('HIGH','EASY',2),       ('HIGH','MEDIUM',4),      ('HIGH','HARD',8),      ('HIGH','COMPLEX',12),
  ('MEDIUM','EASY',4),     ('MEDIUM','MEDIUM',8),    ('MEDIUM','HARD',16),   ('MEDIUM','COMPLEX',24),
  ('LOW','EASY',8),        ('LOW','MEDIUM',16),      ('LOW','HARD',24),      ('LOW','COMPLEX',48);

-- 5. Enforce completeness + the new composite key.
ALTER TABLE sla_configs ALTER COLUMN complexity SET NOT NULL;
ALTER TABLE sla_configs ADD CONSTRAINT uq_sla_priority_complexity UNIQUE (priority, complexity);

-- 6. Add complexity to incidents explicitly (Flyway runs before Hibernate
--    ddl-auto, so we own the column here) and backfill existing rows to MEDIUM
--    so dueDate recomputation and analytics always have a value.
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS complexity VARCHAR(20);
UPDATE incidents SET complexity = 'MEDIUM' WHERE complexity IS NULL;
