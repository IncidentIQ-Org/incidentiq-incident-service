-- Add ON DELETE CASCADE to incident child-table FK constraints so incidents
-- can be deleted without manually removing timeline entries and comments first.

-- incident_comments
ALTER TABLE incident_comments
    DROP CONSTRAINT IF EXISTS incident_comments_incident_id_fkey;
ALTER TABLE incident_comments
    ADD CONSTRAINT incident_comments_incident_id_fkey
        FOREIGN KEY (incident_id) REFERENCES incidents(id) ON DELETE CASCADE;

-- incident_timeline
ALTER TABLE incident_timeline
    DROP CONSTRAINT IF EXISTS incident_timeline_incident_id_fkey;
ALTER TABLE incident_timeline
    ADD CONSTRAINT incident_timeline_incident_id_fkey
        FOREIGN KEY (incident_id) REFERENCES incidents(id) ON DELETE CASCADE;
