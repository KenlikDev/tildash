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
