package com.kenlikdev.tildash.learning

import com.kenlikdev.tildash.content.model.ContentId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class LearningProgressSyncTest {
    private val firstTimestamp = Instant.parse("2026-09-28T08:00:00Z")
    private val secondTimestamp = Instant.parse("2026-09-28T09:00:00Z")
    private val lessonId = ContentId("550e8400-e29b-41d4-a716-446655440000")
    private val exerciseId = "exercise-1"

    @Test
    fun newAttemptsAreMergedAndCanonicallyOrdered() {
        val incoming =
            LearningProgressSyncBatch(
                deviceId = "device-b",
                attempts =
                    listOf(
                        attempt("b", secondTimestamp),
                        attempt("a", firstTimestamp),
                    ),
            )

        val result = LearningProgressSync.merge(LearningProgress.empty(), incoming)

        assertEquals(listOf("a", "b"), result.progress.attempts.map { it.attemptId })
        assertEquals(listOf("a", "b"), result.acknowledgedAttemptIds)
        assertTrue(result.conflicts.isEmpty())
        assertTrue(result.canAcknowledgeBatch)
    }

    @Test
    fun mergeIsIndependentOfIncomingBatchOrder() {
        val first = attempt("attempt-a", firstTimestamp)
        val second = attempt("attempt-b", secondTimestamp, AnswerOutcome.INCORRECT)

        val ascending =
            LearningProgressSync.merge(
                LearningProgress.empty(),
                LearningProgressSyncBatch("device-a", listOf(first, second)),
            )
        val descending =
            LearningProgressSync.merge(
                LearningProgress.empty(),
                LearningProgressSyncBatch("device-a", listOf(second, first)),
            )

        assertEquals(ascending.progress, descending.progress)
        assertEquals(
            ascending.acknowledgedAttemptIds,
            descending.acknowledgedAttemptIds,
        )
        assertEquals(ascending.conflicts, descending.conflicts)
    }

    @Test
    fun repeatedIdenticalDeliveryIsIdempotent() {
        val firstBatch =
            LearningProgressSyncBatch(
                deviceId = "device-a",
                attempts = listOf(attempt("attempt-1", firstTimestamp)),
            )
        val first = LearningProgressSync.merge(LearningProgress.empty(), firstBatch)

        val repeated = LearningProgressSync.merge(
            first.progress,
            firstBatch.copy(deviceId = "device-b"),
        )

        assertEquals(first.progress, repeated.progress)
        assertEquals(listOf("attempt-1"), repeated.acknowledgedAttemptIds)
        assertTrue(repeated.conflicts.isEmpty())
    }

    @Test
    fun identicalAttemptDeliveredByDifferentDevicesConvergesToOneEvent() {
        val local =
            LearningProgressSync.merge(
                LearningProgress.empty(),
                LearningProgressSyncBatch("device-a", listOf(attempt("attempt-1", firstTimestamp))),
            ).progress

        val remote =
            LearningProgressSync.merge(
                LearningProgress.empty(),
                LearningProgressSyncBatch("device-b", listOf(attempt("attempt-1", firstTimestamp))),
            ).progress

        val merged = LearningProgressSync.merge(
            local,
            LearningProgressSyncBatch("device-b", remote.attempts),
        )

        assertEquals(1, merged.progress.attempts.size)
        assertEquals(attempt("attempt-1", firstTimestamp), merged.progress.attempts.single())
        assertEquals(listOf("attempt-1"), merged.acknowledgedAttemptIds)
    }

    @Test
    fun conflictingAttemptIdIsReportedAndExistingEventIsPreserved() {
        val localAttempt = attempt("attempt-1", firstTimestamp, AnswerOutcome.CORRECT)
        val incomingAttempt = attempt("attempt-1", secondTimestamp, AnswerOutcome.INCORRECT)
        val local =
            LearningProgressSync.merge(
                LearningProgress.empty(),
                LearningProgressSyncBatch("device-a", listOf(localAttempt)),
            ).progress

        val result =
            LearningProgressSync.merge(
                local,
                LearningProgressSyncBatch("device-b", listOf(incomingAttempt)),
            )

        assertTrue(result.hasConflicts)
        assertFalse(result.canAcknowledgeBatch)
        assertTrue(result.acknowledgedAttemptIds.isEmpty())
        assertEquals(1, result.conflicts.size)
        assertEquals("attempt-1", result.conflicts.single().attemptId)
        assertEquals(listOf(localAttempt), result.progress.attempts)
    }

    @Test
    fun conflictsDoNotPreventIndependentAttemptsFromMerging() {
        val localAttempt = attempt("attempt-1", firstTimestamp)
        val conflicting = attempt("attempt-1", secondTimestamp)
        val newAttempt = attempt("attempt-2", secondTimestamp)
        val local =
            LearningProgressSync.merge(
                LearningProgress.empty(),
                LearningProgressSyncBatch("device-a", listOf(localAttempt)),
            ).progress

        val result =
            LearningProgressSync.merge(
                local,
                LearningProgressSyncBatch("device-b", listOf(conflicting, newAttempt)),
            )

        assertEquals(listOf("attempt-1", "attempt-2"), result.progress.attempts.map { it.attemptId })
        assertEquals(listOf("attempt-2"), result.acknowledgedAttemptIds)
        assertEquals(listOf("attempt-1"), result.conflicts.map { it.attemptId })
        assertFalse(result.canAcknowledgeBatch)
    }

    @Test
    fun duplicateIdenticalEntriesInsideOneBatchAreAcknowledgedOnce() {
        val duplicate = attempt("attempt-1", firstTimestamp)

        val result =
            LearningProgressSync.merge(
                LearningProgress.empty(),
                LearningProgressSyncBatch(
                    deviceId = "device-a",
                    attempts = listOf(duplicate, duplicate),
                ),
            )

        assertEquals(listOf("attempt-1"), result.progress.attempts.map { it.attemptId })
        assertEquals(listOf("attempt-1"), result.acknowledgedAttemptIds)
        assertTrue(result.conflicts.isEmpty())
    }

    @Test
    fun conflictingEntriesInsideOneBatchAreRejectedWithoutOverwrite() {
        val first = attempt("attempt-1", firstTimestamp)
        val second = attempt("attempt-1", secondTimestamp, AnswerOutcome.INCORRECT)

        val result =
            LearningProgressSync.merge(
                LearningProgress.empty(),
                LearningProgressSyncBatch("device-a", listOf(first, second)),
            )

        assertEquals(emptyList(), result.progress.attempts)
        assertTrue(result.acknowledgedAttemptIds.isEmpty())
        assertEquals(listOf("attempt-1"), result.conflicts.map { it.attemptId })
    }

    @Test
    fun batchDeviceIdMustNotBeBlank() {
        assertFailsWith<IllegalArgumentException> {
            LearningProgressSyncBatch(
                deviceId = " ",
                attempts = emptyList(),
            )
        }
    }

    @Test
    fun mergeResultAcknowledgesOnlyAcceptedOrAlreadyKnownAttempts() {
        val localAttempt = attempt("attempt-1", firstTimestamp)
        val local =
            LearningProgressSync.merge(
                LearningProgress.empty(),
                LearningProgressSyncBatch("device-a", listOf(localAttempt)),
            ).progress

        val result =
            LearningProgressSync.merge(
                local,
                LearningProgressSyncBatch(
                    deviceId = "device-b",
                    attempts =
                        listOf(
                            localAttempt,
                            attempt("attempt-2", secondTimestamp),
                            attempt("attempt-3", secondTimestamp, AnswerOutcome.INCORRECT),
                        ),
                ),
            )

        assertEquals(listOf("attempt-1", "attempt-2", "attempt-3"), result.acknowledgedAttemptIds)
        assertTrue(result.conflicts.isEmpty())
        assertTrue(result.canAcknowledgeBatch)
    }

    private fun attempt(
        attemptId: String,
        occurredAt: Instant,
        outcome: AnswerOutcome = AnswerOutcome.CORRECT,
    ) = LearningAttempt(
        attemptId = attemptId,
        exerciseId = exerciseId,
        response = LearnerResponse.Text(if (outcome == AnswerOutcome.CORRECT) "hello" else "wrong"),
        outcome = outcome,
        occurredAt = occurredAt,
    )
}
