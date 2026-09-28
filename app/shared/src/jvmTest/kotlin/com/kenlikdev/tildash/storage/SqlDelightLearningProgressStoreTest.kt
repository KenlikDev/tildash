package com.kenlikdev.tildash.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kenlikdev.tildash.learning.AnswerOutcome
import com.kenlikdev.tildash.learning.AttemptIdConflict
import com.kenlikdev.tildash.learning.LearnerResponse
import com.kenlikdev.tildash.learning.LearningAttempt
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

class SqlDelightLearningProgressStoreTest {
    @Test
    fun progressAndOutboxSurviveDatabaseReopen() {
        val databaseFile = Files.createTempFile("tildash-learning-", ".db")

        try {
            val attempt =
                LearningAttempt(
                    attemptId = "attempt-1",
                    exerciseId = "exercise-1",
                    response = LearnerResponse.Text("hello"),
                    outcome = AnswerOutcome.CORRECT,
                    occurredAt = Instant.parse("2026-09-28T08:00:00Z"),
                )

            open(databaseFile.toString()).use { store ->
                store.saveAttempt(attempt)

                assertEquals(listOf(attempt), store.loadProgress().attempts)
                assertEquals(listOf(attempt), store.loadPendingSyncAttempts())
                assertEquals(1, store.pendingSyncCount())
            }

            open(databaseFile.toString()).use { store ->
                assertEquals(listOf(attempt), store.loadProgress().attempts)
                assertEquals(listOf(attempt), store.loadPendingSyncAttempts())

                store.acknowledgeAttempt(attempt.attemptId)

                assertEquals(emptyList(), store.loadPendingSyncAttempts())
                assertEquals(0, store.pendingSyncCount())
                assertEquals(listOf(attempt), store.loadProgress().attempts)
            }
        } finally {
            Files.deleteIfExists(databaseFile)
        }
    }

    @Test
    fun duplicateIdenticalAttemptIsIdempotent() {
        val databaseFile = Files.createTempFile("tildash-learning-", ".db")

        try {
            val attempt = attempt()

            open(databaseFile.toString()).use { store ->
                store.saveAttempt(attempt)
                store.saveAttempt(attempt)

                assertEquals(listOf(attempt), store.loadProgress().attempts)
                assertEquals(1, store.pendingSyncCount())
            }
        } finally {
            Files.deleteIfExists(databaseFile)
        }
    }

    @Test
    fun conflictingAttemptIdIsRejectedWithoutOverwrite() {
        val databaseFile = Files.createTempFile("tildash-learning-", ".db")

        try {
            val stored = attempt()
            val conflicting = stored.copy(response = LearnerResponse.Text("wrong"))

            open(databaseFile.toString()).use { store ->
                store.saveAttempt(stored)

                assertFailsWith<AttemptIdConflict> {
                    store.saveAttempt(conflicting)
                }

                assertEquals(listOf(stored), store.loadProgress().attempts)
                assertEquals(listOf(stored), store.loadPendingSyncAttempts())
            }
        } finally {
            Files.deleteIfExists(databaseFile)
        }
    }

    @Test
    fun pendingAttemptsUseDeterministicOrdering() {
        val databaseFile = Files.createTempFile("tildash-learning-", ".db")

        try {
            val first = attempt("attempt-b", Instant.parse("2026-09-28T09:00:00Z"))
            val second = attempt("attempt-a", Instant.parse("2026-09-28T08:00:00Z"))
            val third = attempt("attempt-c", Instant.parse("2026-09-28T09:00:00Z"))

            open(databaseFile.toString()).use { store ->
                store.saveAttempt(first)
                store.saveAttempt(second)
                store.saveAttempt(third)

                assertEquals(
                    listOf("attempt-a", "attempt-b", "attempt-c"),
                    store.loadPendingSyncAttempts().map { it.attemptId },
                )
                assertTrue(store.pendingSyncCount() == 3L)
            }
        } finally {
            Files.deleteIfExists(databaseFile)
        }
    }

    private fun open(path: String): TestStore {
        val driver = JdbcSqliteDriver("jdbc:sqlite:$path")
        TildashDatabase.Schema.create(driver)
        return TestStore(driver, SqlDelightLearningProgressStore(driver))
    }

    private fun attempt(
        id: String = "attempt-1",
        occurredAt: Instant = Instant.parse("2026-09-28T08:00:00Z"),
    ) = LearningAttempt(
        attemptId = id,
        exerciseId = "exercise-1",
        response = LearnerResponse.Text("hello"),
        outcome = AnswerOutcome.CORRECT,
        occurredAt = occurredAt,
    )

    private class TestStore(
        private val driver: JdbcSqliteDriver,
        val store: SqlDelightLearningProgressStore,
    ) : AutoCloseable {
        fun saveAttempt(attempt: LearningAttempt) = store.saveAttempt(attempt)

        fun loadProgress() = store.loadProgress()

        fun loadPendingSyncAttempts() = store.loadPendingSyncAttempts()

        fun pendingSyncCount() = store.pendingSyncCount()

        fun acknowledgeAttempt(attemptId: String) = store.acknowledgeAttempt(attemptId)

        override fun close() = driver.close()
    }
}
