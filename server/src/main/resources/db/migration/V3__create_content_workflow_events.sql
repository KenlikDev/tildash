CREATE TABLE tildash.content_workflow_events (
    id UUID PRIMARY KEY,
    content_node_id UUID NOT NULL REFERENCES tildash.content_nodes(id),
    action VARCHAR(32) NOT NULL CHECK (
        action IN ('SUBMIT', 'START_REVIEW', 'FEEDBACK', 'APPROVE', 'REJECT', 'PUBLISH', 'ARCHIVE')
    ),
    from_state VARCHAR(32) NOT NULL CHECK (
        from_state IN ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'PUBLISHED', 'ARCHIVED')
    ),
    to_state VARCHAR(32) NOT NULL CHECK (
        to_state IN ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'PUBLISHED', 'ARCHIVED')
    ),
    actor_subject TEXT NOT NULL,
    reason TEXT,
    validation_outcome VARCHAR(32) CHECK (
        validation_outcome IS NULL
        OR validation_outcome IN ('PASS', 'PASS_WITH_WARNINGS', 'FAIL')
    ),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX content_workflow_events_content_idx
    ON tildash.content_workflow_events(content_node_id, created_at, id);

CREATE OR REPLACE FUNCTION tildash.prevent_published_content_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.state = 'ARCHIVED'
        OR (
            OLD.state = 'PUBLISHED'
            AND NOT (NEW.state = 'ARCHIVED')
        ) THEN
        RAISE EXCEPTION 'Published or archived content cannot be mutated outside archival transition';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER content_nodes_published_mutation_guard
BEFORE UPDATE ON tildash.content_nodes
FOR EACH ROW EXECUTE FUNCTION tildash.prevent_published_content_mutation();

CREATE OR REPLACE FUNCTION tildash.prevent_workflow_history_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Content workflow history is immutable';
END;
$$;

CREATE TRIGGER content_workflow_events_immutable
BEFORE UPDATE OR DELETE ON tildash.content_workflow_events
FOR EACH ROW EXECUTE FUNCTION tildash.prevent_workflow_history_mutation();
