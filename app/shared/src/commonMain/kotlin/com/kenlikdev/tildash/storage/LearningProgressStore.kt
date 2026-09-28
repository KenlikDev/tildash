package com.kenlikdev.tildash.storage

import app.cash.sqldelight.db.SqlDriver
import com.kenlikdev.tildash.learning.AnswerOutcome
import com.kenlikdev.tildash.learning.AttemptIdConflict
import com.kenlikdev.tildash.learning.LearnerResponse
import com.kenlikdev.tildash.learning.LearningAttempt
import com.kenlikdev.tildash.learning.LearningProgress
import kotlin.time.Instant

interface LearningProgressStore {
    fun loadProgress(): LearningProgress

    fun saveAttempt(attempt: LearningAttempt)

    fun loadPendingSyncAttempts(): List<LearningAttempt>

    fun acknowledgeAttempt(attemptId: String)

    fun pendingSyncCount(): Long
}

class SqlDelightLearningProgressStore(
    driver: SqlDriver,
) : LearningProgressStore {
    private val database = TildashDatabase(driver)
    private val queries = database.learningStorageQueries

    override fun loadProgress(): LearningProgress =
        LearningProgress.fromPersistedAttempts(
            queries.selectAllAttempts().executeAsList().map { row ->
                toLearningAttempt(
                    attemptId = row.attempt_id,
                    exerciseId = row.exercise_id,
                    responseType = row.response_type,
                    responseValue = row.response_value,
                    outcome = row.outcome,
                    occurredAtEpochMillis = row.occurred_at_epoch_millis,
                )
            },
        )

    override fun saveAttempt(attempt: LearningAttempt) {
        database.transaction {
            val existing = queries.selectAttempt(attempt.attemptId).executeAsOneOrNull()
            if (existing != null) {
                val stored =
                    toLearningAttempt(
                        attemptId = existing.attempt_id,
                        exerciseId = existing.exercise_id,
                        responseType = existing.response_type,
                        responseValue = existing.response_value,
                        outcome = existing.outcome,
                        occurredAtEpochMillis = existing.occurred_at_epoch_millis,
                    )
                if (stored != attempt) {
                    throw AttemptIdConflict(attempt.attemptId)
                }
                return@transaction
            }

            val row = toRow(attempt)
            queries.insertAttempt(
                attempt_id = row.attemptId,
                exercise_id = row.exerciseId,
                response_type = row.responseType,
                response_value = row.responseValue,
                outcome = row.outcome,
                occurred_at_epoch_millis = row.occurredAtEpochMillis,
            )
            queries.queueAttempt(attempt.attemptId)
        }
    }

    override fun loadPendingSyncAttempts(): List<LearningAttempt> =
        queries.selectPendingAttempts().executeAsList().map { row ->
            toLearningAttempt(
                attemptId = row.attempt_id,
                exerciseId = row.exercise_id,
                responseType = row.response_type,
                responseValue = row.response_value,
                outcome = row.outcome,
                occurredAtEpochMillis = row.occurred_at_epoch_millis,
            )
        }

    override fun acknowledgeAttempt(attemptId: String) {
        database.transaction {
            queries.deleteOutboxAttempt(attemptId)
        }
    }

    override fun pendingSyncCount(): Long =
        queries.pendingAttemptCount().executeAsOne()

    private data class AttemptRow(
        val attemptId: String,
        val exerciseId: String,
        val responseType: String,
        val responseValue: String,
        val outcome: String,
        val occurredAtEpochMillis: Long,
    )

    private fun toRow(attempt: LearningAttempt): AttemptRow {
        val response =
            when (val value = attempt.response) {
                is LearnerResponse.Text -> AttemptRowResponse("TEXT", value.value)
            }

        return AttemptRow(
            attemptId = attempt.attemptId,
            exerciseId = attempt.exerciseId,
            responseType = response.type,
            responseValue = response.value,
            outcome = attempt.outcome.name,
            occurredAtEpochMillis = attempt.occurredAt.toEpochMilliseconds(),
        )
    }

    private fun toLearningAttempt(
        attemptId: String,
        exerciseId: String,
        responseType: String,
        responseValue: String,
        outcome: String,
        occurredAtEpochMillis: Long,
    ): LearningAttempt =
        LearningAttempt(
            attemptId = attemptId,
            exerciseId = exerciseId,
            response =
                when (responseType) {
                    "TEXT" -> LearnerResponse.Text(responseValue)
                    else -> error("Unsupported persisted learner response type '$responseType'.")
                },
            outcome =
                runCatching { AnswerOutcome.valueOf(outcome) }
                    .getOrElse {
                        error("Unsupported persisted answer outcome '$outcome'.")
                    },
            occurredAt = Instant.fromEpochMilliseconds(occurredAtEpochMillis),
        )

    private data class AttemptRowResponse(
        val type: String,
        val value: String,
    )
}
