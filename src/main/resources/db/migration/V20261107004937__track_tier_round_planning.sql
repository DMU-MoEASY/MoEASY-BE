-- Advance only after every page for the day has been persisted.
ALTER TABLE attendance_cutover ADD planned_through DATE;
UPDATE attendance_cutover SET planned_through=DATE(established_at);
