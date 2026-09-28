package com.kenlikdev.tildash.server.learning

import com.kenlikdev.tildash.learning.AnswerOutcome
import com.kenlikdev.tildash.learning.LearnerResponse
import com.kenlikdev.tildash.learning.LearningAttempt
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.Instant

@Repository
class LearningSyncRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    fun insertOrFindExisting(
        learnerSubject: String,
        attempt: LearningAttempt,
    ): LearningAttemptRecord {
        val response =
            attempt.response as? LearnerResponse.Text
                ?: error("Unsupported learner response type.")

        val params =
            MapSqlParameterSource()
                .addValue("learnerSubject", learnerSubject)
                .addValue("attemptId", attempt.attemptId)
                .addValue("exerciseId", attempt.exerciseId)
                .addValue("responseType", "TEXT")
                .addValue("responseValue", response.value)
                .addValue("outcome", attempt.outcome.name)
                .addValue("occurredAt", attempt.occurredAt.toOffsetDateTime())

        val inserted =
            jdbc.update(
                """
                insert into tildash.learning_attempts (
                    learner_subject, attempt_id, exercise_id, response_type,
                    response_value, outcome, occurred_at
                )
                values (
                    :learnerSubject, :attemptId, :exerciseId, :responseType,
                    :responseValue, :outcome, :occurredAt
                )
                on conflict (learner_subject, attempt_id) do nothing
                """.trimIndent(),
                params,
            )

        if (inserted == 1) {
            return LearningAttemptRecord.fromAttempt(attempt)
        }

        return jdbc
            .query(
                """
                select attempt_id, exercise_id, response_type, response_value,
                       outcome, occurred_at
                from tildash.learning_attempts
                where learner_subject = :learnerSubject
                  and attempt_id = :attemptId
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("learnerSubject", learnerSubject)
                    .addValue("attemptId", attempt.attemptId),
            ) { rs, _ ->
                LearningAttemptRecord(
                    attemptId = rs.getString("attempt_id"),
                    exerciseId = rs.getString("exercise_id"),
                    responseType = rs.getString("response_type"),
                    responseValue = rs.getString("response_value"),
                    outcome = AnswerOutcome.valueOf(rs.getString("outcome")),
                    occurredAt = rs.readInstant("occurred_at"),
                )
            }
            .firstOrNull()
            ?: error("Learning attempt disappeared after conflict detection.")
    }
}

data class LearningAttemptRecord(
    val attemptId: String,
    val exerciseId: String,
    val responseType: String,
    val responseValue: String,
    val outcome: AnswerOutcome,
    val occurredAt: Instant,
) {
    fun matches(attempt: LearningAttempt): Boolean =
        responseType == "TEXT" &&
            attempt.exerciseId == exerciseId &&
            (attempt.response as? LearnerResponse.Text)?.value == responseValue &&
            attempt.outcome == outcome &&
            attempt.occurredAt == occurredAt

    companion object {
        fun fromAttempt(attempt: LearningAttempt): LearningAttemptRecord {
            val response =
                attempt.response as? LearnerResponse.Text
                    ?: error("Unsupported learner response type.")

            return LearningAttemptRecord(
                attemptId = attempt.attemptId,
                exerciseId = attempt.exerciseId,
                responseType = "TEXT",
                responseValue = response.value,
                outcome = attempt.outcome,
                occurredAt = attempt.occurredAt,
            )
        }
    }
}

private fun ResultSet.readInstant(column: String): Instant =
    getObject(column, OffsetDateTime::class.java)
        ?.let { Instant.fromEpochSeconds(it.toEpochSecond(), it.nano.toLong()) }
        ?: error("Persisted learning attempt is missing $column.")

private fun Instant.toOffsetDateTime(): OffsetDateTime =
    OffsetDateTime.ofInstant(
        java.time.Instant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong()),
        ZoneOffset.UTC,
    )
