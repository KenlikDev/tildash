DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM tildash.content_published_exercises AS exercise
        JOIN tildash.content_published_versions AS version
          ON version.id = exercise.published_version_id
        WHERE exercise.content_node_id IS DISTINCT FROM version.content_node_id
    ) THEN
        RAISE EXCEPTION
            'Cannot enforce published exercise ownership: existing rows reference a different node than their published version';
    END IF;
END;
$$;

ALTER TABLE tildash.content_published_versions
    ADD CONSTRAINT content_published_versions_id_content_node_unique
    UNIQUE (id, content_node_id);

ALTER TABLE tildash.content_published_exercises
    ADD CONSTRAINT content_published_exercises_version_node_fk
    FOREIGN KEY (published_version_id, content_node_id)
    REFERENCES tildash.content_published_versions (id, content_node_id);
