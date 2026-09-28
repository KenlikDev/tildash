package com.kenlikdev.tildash.server.learning

import com.kenlikdev.tildash.learning.AnswerOutcome
import com.kenlikdev.tildash.learning.LearnerResponse
import com.kenlikdev.tildash.learning.LearningAttempt
import com.kenlikdev.tildash.server.api.learning.LearningAttemptRequest
import com.kenlikdev.tildash.server.api.learning.LearningOutcomeRequest
import com.kenlikdev.tildash.server.api.learning.LearningResponseType
import com.kenlikdev.tildash.server.api.learning.LearningSyncRequest
import com.kenlikdev.tildash.server.api.learning.LearningSyncResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.time.Instant

@Service
class LearningSyncService(
    private val repository: LearningSyncRepository,
) {
    @Transactional
    fun synchronize(
        learnerSubject: String,
        request: LearningSyncRequest,
    ): LearningSyncResponse {
        val acknowledged = mutableListOf<String>()
        val conflicts = mutableListOf<String>()

        request.attempts.forEach { attemptRequest ->
            val attempt = attemptRequest.toDomain()
            val stored = repository.insertOrFindExisting(learnerSubject, attempt)

            if (stored.matches(attempt)) {
                acknowledged += attempt.attemptId
            } else {
                conflicts += attempt.attemptId
            }
        }

        return LearningSyncResponse(
            acknowledgedAttemptIds = acknowledged.distinct().sorted(),
            conflictAttemptIds = conflicts.distinct().sorted(),
        )
    }
}

private fun LearningAttemptRequest.toDomain(): LearningAttempt {
    check(response.type == LearningResponseType.TEXT) {
        "Unsupported learning response type: " + response.type + "."
    }

    return LearningAttempt(
        attemptId = attemptId,
        exerciseId = exerciseId,
        response = LearnerResponse.Text(response.value),
        outcome =
            when (outcome) {
                LearningOutcomeRequest.CORRECT -> AnswerOutcome.CORRECT
                LearningOutcomeRequest.INCORRECT -> AnswerOutcome.INCORRECT
            },
        occurredAt = Instant.fromEpochSeconds(occurredAt.toEpochSecond(), occurredAt.nano.toLong()),
    )
}
