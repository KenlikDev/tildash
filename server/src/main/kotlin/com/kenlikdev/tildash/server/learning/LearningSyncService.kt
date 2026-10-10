package com.kenlikdev.tildash.server.learning

import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.learning.AnswerEvaluator
import com.kenlikdev.tildash.learning.AnswerOutcome
import com.kenlikdev.tildash.learning.LearnerResponse
import com.kenlikdev.tildash.learning.LearningAttempt
import com.kenlikdev.tildash.learning.LearningExercise
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
    private val learningCatalogService: LearningCatalogService,
) {
    @Transactional
    fun synchronize(
        learnerSubject: String,
        request: LearningSyncRequest,
    ): LearningSyncResponse {
        val exercisesByVersion = mutableMapOf<Pair<ContentId, Int>, Map<String, LearningExercise>>()
        val unavailableVersions = mutableSetOf<Pair<ContentId, Int>>()
        val preparedAttempts =
            request.attempts.map { item ->
                val attempt = item.toDomain()
                val evaluated =
                    try {
                        evaluateAgainstPublishedVersion(attempt, exercisesByVersion, unavailableVersions)
                    } catch (_: LearningSyncAttemptRejected) {
                        null
                    }
                PreparedAttempt(attemptId = attempt.attemptId, attempt = evaluated)
            }

        val acknowledged = mutableListOf<String>()
        val conflicts = mutableListOf<String>()

        preparedAttempts
            .groupBy { it.attemptId }
            .toSortedMap()
            .forEach { (attemptId, group) ->
                if (group.any { it.attempt == null }) {
                    conflicts += attemptId
                    return@forEach
                }

                val attempts = group.map { requireNotNull(it.attempt) }
                if (attempts.distinct().size > 1) {
                    conflicts += attemptId
                    return@forEach
                }

                val canonicalAttempt = attempts.first()
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

    private fun evaluateAgainstPublishedVersion(
        attempt: LearningAttempt,
        cache: MutableMap<Pair<ContentId, Int>, Map<String, LearningExercise>>,
        unavailableVersions: MutableSet<Pair<ContentId, Int>>,
    ): LearningAttempt {
        val lessonId = attempt.lessonId ?: throw LearningSyncAttemptRejected()
        val publishedVersion = attempt.publishedVersion ?: throw LearningSyncAttemptRejected()
        val key = lessonId to publishedVersion
        if (key in unavailableVersions) throw LearningSyncAttemptRejected()

        val exercises =
            cache[key] ?: run {
                val loaded = learningCatalogService.publishedExercises(lessonId, publishedVersion)
                if (loaded == null) {
                    unavailableVersions += key
                    throw LearningSyncAttemptRejected()
                }
                loaded.associateBy { it.id }.also { cache[key] = it }
            }

        val exercise = exercises[attempt.exerciseId] ?: throw LearningSyncAttemptRejected()
        val evaluation = AnswerEvaluator.evaluate(exercise, attempt.response)
        return attempt.copy(outcome = evaluation.outcome)
    }

    private data class PreparedAttempt(
        val attemptId: String,
        val attempt: LearningAttempt?,
    )
}

class LearningSyncInvalidRequestException : RuntimeException()

private class LearningSyncAttemptRejected : RuntimeException()

private fun LearningAttemptRequest.toDomain(): LearningAttempt {
    if (response.type != LearningResponseType.TEXT) {
        throw LearningSyncInvalidRequestException()
    }
    val databasePrecisionOccurredAt = occurredAt.withNano((occurredAt.nano / 1_000) * 1_000)

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
        occurredAt =
            Instant.fromEpochSeconds(
                databasePrecisionOccurredAt.toEpochSecond(),
                databasePrecisionOccurredAt.nano.toLong(),
            ),
        publishedVersion = publishedVersion,
    )
}
