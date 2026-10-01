package com.kenlikdev.tildash.server.learning

import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.learning.AnswerOutcome
import com.kenlikdev.tildash.learning.LearnerResponse
import com.kenlikdev.tildash.learning.LearningAttempt
import com.kenlikdev.tildash.server.api.learning.LearningAttemptRequest
import com.kenlikdev.tildash.server.api.learning.LearningOutcomeRequest
import com.kenlikdev.tildash.server.api.learning.LearningResponseType
import com.kenlikdev.tildash.server.api.learning.LearningSyncRequest
import com.kenlikdev.tildash.server.api.learning.LearningSyncResponse
import java.time.OffsetDateTime
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
        val parsedAttempts = request.attempts.map(LearningAttemptRequest::toDomain)
        val acknowledged = mutableListOf<String>()
        val conflicts = mutableListOf<String>()

        parsedAttempts
            .groupBy { it.attemptId }
            .toSortedMap()
            .forEach { (attemptId, attempts) ->
                val canonicalAttempt = attempts.first()
                if (attempts.distinct().size > 1) {
                    conflicts += attemptId
                    return@forEach
                }

                val stored = repository.insertOrFindExisting(learnerSubject, canonicalAttempt)
                if (stored.matches(canonicalAttempt)) {
                    acknowledged += attemptId
                } else {
                    conflicts += attemptId
                }
            }

        return LearningSyncResponse(
            acknowledgedAttemptIds = acknowledged.sorted(),
            conflictAttemptIds = conflicts.sorted(),
        )
    }
}

private fun LearningAttemptRequest.toDomain(): LearningAttempt {
    check(response.type == LearningResponseType.TEXT) {
        "Unsupported learning response type: " + response.type + "."
    }

    return LearningAttempt(
        attemptId = attemptId,
        lessonId = ContentId(lessonId.toString()),
        exerciseId = exerciseId,
        response = LearnerResponse.Text(response.value),
        outcome =
            when (outcome) {
                LearningOutcomeRequest.CORRECT -> AnswerOutcome.CORRECT
                LearningOutcomeRequest.INCORRECT -> AnswerOutcome.INCORRECT
            },
        occurredAt = occurredAt.toPostgresPrecision(),
    )
}

private fun OffsetDateTime.toPostgresPrecision(): Instant =
    Instant.fromEpochSeconds(
        toEpochSecond(),
        (nano / 1_000L) * 1_000L,
    )
