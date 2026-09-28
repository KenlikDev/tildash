package com.kenlikdev.tildash.storage

import com.kenlikdev.tildash.learning.AnswerOutcome
import com.kenlikdev.tildash.learning.LearningAttempt
import com.kenlikdev.tildash.learning.LearnerResponse
import com.kenlikdev.tildash.learning.LearningProgress
import com.kenlikdev.tildash.learning.LearningProgressSyncBatch
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

class LearningSyncCoordinatorTest {
    private val first = attempt("attempt-b", "2026-09-28T09:00:00Z")
    private val second = attempt("attempt-a", "2026-09-28T08:00:00Z")

    @Test
    fun emptyOutboxDoesNotCallTransport() =
        runTest {
        val store = FakeStore()
        var calls = 0
        val transport =
            object : LearningSyncTransport {
                override suspend fun synchronize(batch: LearningProgressSyncBatch): LearningSyncTransportResult {
                    calls += 1
                    error("transport must not be called")
                }
            }

        val report =
            LearningSyncCoordinator(
                store = store,
                transport = transport,
                delayBeforeRetry = {},
            ).synchronize("device-a")

        assertEquals(0, calls)
        assertEquals(emptyList(), report.submittedAttemptIds)
        assertEquals(0, report.retryCount)
    }

    @Test
    fun pendingAttemptsAreSubmittedInDeterministicOrder() =
        runTest {
        val store = FakeStore(first, second)
        var received: List<String> = emptyList()
        val transport =
            object : LearningSyncTransport {
                override suspend fun synchronize(batch: LearningProgressSyncBatch): LearningSyncTransportResult {
                    received = batch.attempts.map { it.attemptId }
                    return LearningSyncTransportResult.Succeeded(
                        acknowledgedAttemptIds = received,
                    )
                }
            }

        val report =
            LearningSyncCoordinator(
                store = store,
                transport = transport,
                delayBeforeRetry = {},
            ).synchronize("device-a")

        assertEquals(listOf("attempt-a", "attempt-b"), received)
        assertEquals(listOf("attempt-a", "attempt-b"), report.acknowledgedAttemptIds)
        assertEquals(emptyList(), store.pendingIds())
    }

    @Test
    fun partialAcknowledgementLeavesConflictsInOutbox() =
        runTest {
        val store = FakeStore(first, second)
        val transport =
            object : LearningSyncTransport {
                override suspend fun synchronize(batch: LearningProgressSyncBatch) =
                    LearningSyncTransportResult.Succeeded(
                    acknowledgedAttemptIds = listOf("attempt-a"),
                    conflictAttemptIds = listOf("attempt-b"),
                )
            }

        val report =
            LearningSyncCoordinator(
                store = store,
                transport = transport,
                delayBeforeRetry = {},
            ).synchronize("device-a")

        assertEquals(listOf("attempt-a", "attempt-b"), report.submittedAttemptIds)
        assertEquals(listOf("attempt-a"), report.acknowledgedAttemptIds)
        assertEquals(listOf("attempt-b"), report.conflictAttemptIds)
        assertTrue(report.hasConflicts)
        assertEquals(listOf("attempt-b"), store.pendingIds())
    }

    @Test
    fun transientFailuresAreRetriedWithDeterministicDelays() =
        runTest {
        val store = FakeStore(first)
        val delays = mutableListOf<Long>()
        var calls = 0
        val transport =
            object : LearningSyncTransport {
                override suspend fun synchronize(
                    batch: LearningProgressSyncBatch,
                ): LearningSyncTransportResult {
                    calls += 1
                    if (calls < 3) {
                        throw TransientLearningSyncFailure("temporary outage")
                    }
                    return LearningSyncTransportResult.Succeeded(
                        acknowledgedAttemptIds = listOf("attempt-b"),
                    )
                }
            }

        val policy =
            LearningSyncRetryPolicy(
                maxAttempts = 4,
                initialDelayMillis = 100,
                multiplier = 2,
                maxDelayMillis = 500,
            )

        val report =
            LearningSyncCoordinator(
                store = store,
                transport = transport,
                retryPolicy = policy,
                delayBeforeRetry = { delays += it },
            ).synchronize("device-a")

        assertEquals(3, calls)
        assertEquals(listOf(100L, 200L), delays)
        assertEquals(2, report.retryCount)
        assertEquals(emptyList(), store.pendingIds())
    }

    @Test
    fun maxAttemptsPropagatesTheLastTransientFailure() =
        runTest {
        val store = FakeStore(first)
        var calls = 0
        val transport =
            object : LearningSyncTransport {
                override suspend fun synchronize(
                    batch: LearningProgressSyncBatch,
                ): LearningSyncTransportResult {
                    calls += 1
                    throw TransientLearningSyncFailure("temporary outage")
                }
            }

        val failure =
            assertFailsWith<TransientLearningSyncFailure> {
                LearningSyncCoordinator(
                    store = store,
                    transport = transport,
                    retryPolicy = LearningSyncRetryPolicy(maxAttempts = 2),
                    delayBeforeRetry = {},
                ).synchronize("device-a")
            }

        assertEquals("temporary outage", failure.message)
        assertEquals(2, calls)
        assertEquals(listOf("attempt-b"), store.pendingIds())
    }

    @Test
    fun nonTransientFailuresAreNotRetried() =
        runTest {
        val store = FakeStore(first)
        var calls = 0
        val transport =
            object : LearningSyncTransport {
                override suspend fun synchronize(
                    batch: LearningProgressSyncBatch,
                ): LearningSyncTransportResult {
                    calls += 1
                    throw IllegalStateException("permanent")
                }
            }

        assertFailsWith<IllegalStateException> {
            LearningSyncCoordinator(
                store = store,
                transport = transport,
                delayBeforeRetry = {},
            ).synchronize("device-a")
        }

        assertEquals(1, calls)
        assertEquals(listOf("attempt-b"), store.pendingIds())
    }

    @Test
    fun transportCannotAcknowledgeUnknownAttempt() =
        runTest {
        val store = FakeStore(first)
        val transport =
            object : LearningSyncTransport {
                override suspend fun synchronize(batch: LearningProgressSyncBatch) =
                    LearningSyncTransportResult.Succeeded(
                    acknowledgedAttemptIds = listOf("unknown"),
                )
            }

        assertFailsWith<IllegalArgumentException> {
            LearningSyncCoordinator(
                store = store,
                transport = transport,
                delayBeforeRetry = {},
            ).synchronize("device-a")
        }

        assertEquals(listOf("attempt-b"), store.pendingIds())
    }

    @Test
    fun transportCannotAcknowledgeAndConflictSameAttempt() =
        runTest {
        val store = FakeStore(first)
        val transport =
            object : LearningSyncTransport {
                override suspend fun synchronize(
                    batch: LearningProgressSyncBatch,
                ) = LearningSyncTransportResult.Succeeded(
                    acknowledgedAttemptIds = listOf("attempt-b"),
                    conflictAttemptIds = listOf("attempt-b"),
                )
            }

        assertFailsWith<IllegalArgumentException> {
            LearningSyncCoordinator(
                store = store,
                transport = transport,
                delayBeforeRetry = {},
            ).synchronize("device-a")
        }

        assertEquals(listOf("attempt-b"), store.pendingIds())
    }

    @Test
    fun retryPolicyUsesExponentialBackoffAndCapsDelay() {
        val policy =
            LearningSyncRetryPolicy(
                maxAttempts = 5,
                initialDelayMillis = 100,
                multiplier = 3,
                maxDelayMillis = 500,
            )

        assertEquals(100L, policy.delayBeforeRetry(1))
        assertEquals(300L, policy.delayBeforeRetry(2))
        assertEquals(500L, policy.delayBeforeRetry(3))
        assertEquals(500L, policy.delayBeforeRetry(4))
    }

    private fun attempt(
        id: String,
        timestamp: String,
    ) = LearningAttempt(
        attemptId = id,
        exerciseId = "exercise-1",
        response = LearnerResponse.Text("hello"),
        outcome = AnswerOutcome.CORRECT,
        occurredAt = Instant.parse(timestamp),
    )

    private fun runTest(block: suspend () -> Unit) {
        var failure: Throwable? = null
        blockWithContinuation(block) { failure = it }
        failure?.let { throw it }
    }

    private fun blockWithContinuation(
        block: suspend () -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        block.startCoroutine(
            object : kotlin.coroutines.Continuation<Unit> {
                override val context = kotlin.coroutines.EmptyCoroutineContext

                override fun resumeWith(result: Result<Unit>) {
                    result.exceptionOrNull()?.let(onFailure)
                }
            },
        )
    }

    private class FakeStore(
        vararg attempts: LearningAttempt,
    ) : LearningProgressStore {
        private val progress = attempts.toMutableList()
        private val pending = attempts.mapTo(linkedSetOf()) { it.attemptId }

        override fun loadProgress(): LearningProgress = LearningProgress.fromPersistedAttempts(progress)

        override fun saveAttempt(attempt: LearningAttempt) {
            if (attempt.attemptId !in progress.map { it.attemptId }) {
                progress += attempt
                pending += attempt.attemptId
            }
        }

        override fun loadPendingSyncAttempts(): List<LearningAttempt> =
            progress
                .filter { it.attemptId in pending }
                .sortedWith(compareBy<LearningAttempt> { it.occurredAt }.thenBy { it.attemptId })

        override fun acknowledgeAttempt(attemptId: String) {
            pending.remove(attemptId)
        }

        override fun pendingSyncCount(): Long = pending.size.toLong()

        fun pendingIds(): List<String> = loadPendingSyncAttempts().map { it.attemptId }
    }
}
