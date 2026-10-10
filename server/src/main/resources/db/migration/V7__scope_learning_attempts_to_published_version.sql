ALTER TABLE tildash.learning_attempts
    ADD COLUMN published_version INTEGER CHECK (published_version IS NULL OR published_version > 0);
