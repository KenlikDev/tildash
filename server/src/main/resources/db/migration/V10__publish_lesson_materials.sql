ALTER TABLE tildash.content_nodes
    DROP CONSTRAINT content_nodes_kind_check;

ALTER TABLE tildash.content_nodes
    ADD CONSTRAINT content_nodes_kind_check
    CHECK (kind IN ('COURSE', 'LESSON', 'EXPLANATION', 'EXAMPLE', 'VOCABULARY', 'MEDIA_REFERENCE'));

CREATE TABLE tildash.content_published_lesson_materials (
    published_version_id UUID NOT NULL,
    lesson_content_node_id UUID NOT NULL,
    material_content_node_id UUID NOT NULL REFERENCES tildash.content_nodes(id),
    kind VARCHAR(32) NOT NULL CHECK (kind IN ('EXPLANATION', 'EXAMPLE', 'VOCABULARY')),
    position INTEGER NOT NULL CHECK (position >= 0),
    source_locale TEXT NOT NULL,
    payload JSONB NOT NULL,
    PRIMARY KEY (published_version_id, material_content_node_id),
    UNIQUE (published_version_id, position),
    FOREIGN KEY (published_version_id, lesson_content_node_id)
        REFERENCES tildash.content_published_versions (id, content_node_id)
);

CREATE INDEX content_published_lesson_materials_position_idx
    ON tildash.content_published_lesson_materials(published_version_id, position);

CREATE TRIGGER content_published_lesson_materials_immutable
BEFORE UPDATE OR DELETE ON tildash.content_published_lesson_materials
FOR EACH ROW EXECUTE FUNCTION tildash.prevent_content_history_mutation();
