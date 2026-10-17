CREATE TABLE tildash.content_exercise_definitions (
    content_node_id UUID NOT NULL REFERENCES tildash.content_nodes(id) ON DELETE CASCADE,
    exercise_id TEXT NOT NULL,
    position INTEGER NOT NULL CHECK (position >= 0),
    exercise_type VARCHAR(32) NOT NULL CHECK (exercise_type IN ('MANUAL_INPUT')),
    prompt TEXT NOT NULL,
    expected_answers JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (content_node_id, exercise_id)
);

CREATE INDEX content_exercise_definitions_content_position_idx
    ON tildash.content_exercise_definitions(content_node_id, position, exercise_id);

CREATE TABLE tildash.content_published_exercises (
    published_version_id UUID NOT NULL REFERENCES tildash.content_published_versions(id),
    content_node_id UUID NOT NULL REFERENCES tildash.content_nodes(id),
    exercise_id TEXT NOT NULL,
    position INTEGER NOT NULL CHECK (position >= 0),
    exercise_type VARCHAR(32) NOT NULL CHECK (exercise_type IN ('MANUAL_INPUT')),
    prompt TEXT NOT NULL,
    expected_answers JSONB NOT NULL,
    PRIMARY KEY (published_version_id, exercise_id)
);

CREATE INDEX content_published_exercises_content_idx
    ON tildash.content_published_exercises(content_node_id, position, exercise_id);

CREATE TRIGGER content_published_exercises_immutable
BEFORE UPDATE OR DELETE ON tildash.content_published_exercises
FOR EACH ROW EXECUTE FUNCTION tildash.prevent_content_history_mutation();
