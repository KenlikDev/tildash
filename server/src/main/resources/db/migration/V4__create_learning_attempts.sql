CREATE TABLE tildash.learning_attempts (
    learner_subject TEXT NOT NULL,
    attempt_id TEXT NOT NULL,
    exercise_id TEXT NOT NULL,
    response_type VARCHAR(32) NOT NULL CHECK (response_type IN ('TEXT')),
    response_value TEXT NOT NULL,
    outcome VARCHAR(16) NOT NULL CHECK (outcome IN ('CORRECT', 'INCORRECT')),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (learner_subject, attempt_id)
);

CREATE INDEX learning_attempts_subject_time_idx
    ON tildash.learning_attempts(learner_subject, occurred_at, attempt_id);

CREATE OR REPLACE FUNCTION tildash.prevent_learning_attempt_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Learning attempt history is immutable';
END;
$$;

CREATE TRIGGER learning_attempts_immutable
BEFORE UPDATE OR DELETE ON tildash.learning_attempts
FOR EACH ROW EXECUTE FUNCTION tildash.prevent_learning_attempt_mutation();
