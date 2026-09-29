ALTER TABLE tildash.learning_attempts
    ADD COLUMN lesson_id UUID;

CREATE INDEX learning_attempts_subject_lesson_exercise_idx
    ON tildash.learning_attempts(learner_subject, lesson_id, exercise_id);
