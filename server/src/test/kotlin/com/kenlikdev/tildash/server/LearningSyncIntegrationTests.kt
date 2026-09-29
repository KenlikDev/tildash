package com.kenlikdev.tildash.server

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration::class)
class LearningSyncIntegrationTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun syncRequiresAuthentication() {
        mockMvc
            .perform(
                post("/api/v1/learning/sync")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson("attempt-unauthenticated")),
            ).andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("urn:tildash:problem:unauthorized"))
    }

    @Test
    fun nonLearnerCannotUseLearningSync() {
        mockMvc
            .perform(
                post("/api/v1/learning/sync")
                    .with(user("teacher-a").roles("TEACHER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson("attempt-teacher")),
            ).andExpect(status().isForbidden)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("urn:tildash:problem:forbidden"))
    }

    @Test
    fun newAttemptIsAcknowledgedAndPersisted() {
        mockMvc
            .perform(authenticatedSync("learner-a", requestJson("attempt-new")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.acknowledgedAttemptIds[0]").value("attempt-new"))
            .andExpect(jsonPath("$.conflictAttemptIds").isEmpty)

        assertEquals(1, countAttempts("learner-a", "attempt-new"))
    }

    @Test
    fun identicalRetryIsAcknowledgedWithoutDuplicateRow() {
        val request = requestJson("attempt-idempotent")

        repeat(2) {
            mockMvc
                .perform(authenticatedSync("learner-a", request))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.acknowledgedAttemptIds[0]").value("attempt-idempotent"))
                .andExpect(jsonPath("$.conflictAttemptIds").isEmpty)
        }

        assertEquals(1, countAttempts("learner-a", "attempt-idempotent"))
    }

    @Test
    fun sameAttemptIdAndExerciseIdAcrossLessonsIsReportedAsConflict() {
        mockMvc
            .perform(
                authenticatedSync(
                    "learner-a",
                    requestJson(
                        "attempt-cross-lesson",
                        lessonId = "550e8400-e29b-41d4-a716-446655440000",
                    ),
                ),
            ).andExpect(status().isOk)

        mockMvc
            .perform(
                authenticatedSync(
                    "learner-a",
                    requestJson(
                        "attempt-cross-lesson",
                        lessonId = "550e8400-e29b-41d4-a716-446655440001",
                    ),
                ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.acknowledgedAttemptIds").isEmpty)
            .andExpect(jsonPath("$.conflictAttemptIds[0]").value("attempt-cross-lesson"))

        assertEquals(
            "550e8400-e29b-41d4-a716-446655440000",
            jdbcTemplate.queryForObject(
                "select lesson_id::text from tildash.learning_attempts where learner_subject = ? and attempt_id = ?",
                String::class.java,
                "learner-a",
                "attempt-cross-lesson",
            ),
        )
    }

    @Test
    fun conflictingRetryIsReportedAndExistingPayloadIsPreserved() {
        mockMvc
            .perform(
                authenticatedSync(
                    "learner-a",
                    requestJson(
                        "attempt-conflict",
                        "CORRECT",
                        "2026-09-28T09:00:00Z",
                    ),
                ),
            ).andExpect(status().isOk)

        mockMvc
            .perform(
                authenticatedSync(
                    "learner-a",
                    requestJson(
                        "attempt-conflict",
                        "INCORRECT",
                        "2026-09-28T10:00:00Z",
                    ),
                ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.acknowledgedAttemptIds").isEmpty)
            .andExpect(jsonPath("$.conflictAttemptIds[0]").value("attempt-conflict"))

        assertEquals("CORRECT", findOutcome("learner-a", "attempt-conflict"))
    }

    @Test
    fun contradictoryDuplicateAttemptIdsInOneBatchAreReportedAsConflict() {
        mockMvc
            .perform(
                authenticatedSync(
                    "learner-a",
                    contradictoryDuplicateRequest(),
                ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.acknowledgedAttemptIds").isEmpty)
            .andExpect(jsonPath("$.conflictAttemptIds[0]").value("attempt-duplicate"))

        assertEquals(0, countAttempts("learner-a", "attempt-duplicate"))
    }

    @Test
    fun sameAttemptIdIsScopedToAuthenticatedLearner() {
        val request = requestJson("attempt-shared-id")

        mockMvc
            .perform(authenticatedSync("learner-a", request))
            .andExpect(status().isOk)

        mockMvc
            .perform(authenticatedSync("learner-b", request))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.acknowledgedAttemptIds[0]").value("attempt-shared-id"))

        assertEquals(
            2,
            jdbcTemplate.queryForObject(
                "select count(*) from tildash.learning_attempts where attempt_id = ?",
                Int::class.java,
                "attempt-shared-id",
            ),
        )
    }

    @Test
    fun persistedLearningAttemptCannotBeUpdatedOrDeleted() {
        mockMvc
            .perform(authenticatedSync("learner-a", requestJson("attempt-immutable")))
            .andExpect(status().isOk)

        assertFailsWith<DataAccessException> {
            jdbcTemplate.update(
                "update tildash.learning_attempts set outcome = 'INCORRECT' where learner_subject = ? and attempt_id = ?",
                "learner-a",
                "attempt-immutable",
            )
        }

        assertFailsWith<DataAccessException> {
            jdbcTemplate.update(
                "delete from tildash.learning_attempts where learner_subject = ? and attempt_id = ?",
                "learner-a",
                "attempt-immutable",
            )
        }

        assertEquals("CORRECT", findOutcome("learner-a", "attempt-immutable"))
    }

    @Test
    fun invalidDeviceIdUsesProblemDetails() {
        mockMvc
            .perform(
                authenticatedSync(
                    "learner-a",
                    requestJson(
                        "attempt-invalid-device",
                        deviceId = " ",
                    ),
                ),
            ).andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
    }

    private fun authenticatedSync(
        subject: String,
        request: String,
    ) = post("/api/v1/learning/sync")
        .with(user(subject).roles("LEARNER"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(request)

    private fun countAttempts(
        subject: String,
        attemptId: String,
    ): Int =
        jdbcTemplate.queryForObject(
            "select count(*) from tildash.learning_attempts where learner_subject = ? and attempt_id = ?",
            Int::class.java,
            subject,
            attemptId,
        ) ?: 0

    private fun findOutcome(
        subject: String,
        attemptId: String,
    ): String =
        jdbcTemplate.queryForObject(
            "select outcome from tildash.learning_attempts where learner_subject = ? and attempt_id = ?",
            String::class.java,
            subject,
            attemptId,
        ) ?: error("Learning attempt outcome is missing.")

    private fun contradictoryDuplicateRequest(): String =
        """
        {
          "deviceId": "device-a",
          "attempts": [
            {
              "attemptId": "attempt-duplicate",
              "lessonId": "550e8400-e29b-41d4-a716-446655440000",
              "exerciseId": "exercise-1",
              "response": {"type": "TEXT", "value": "hello"},
              "outcome": "CORRECT",
              "occurredAt": "2026-09-28T09:00:00Z"
            },
            {
              "attemptId": "attempt-duplicate",
              "exerciseId": "exercise-1",
              "response": {"type": "TEXT", "value": "wrong"},
              "outcome": "INCORRECT",
              "occurredAt": "2026-09-28T10:00:00Z"
            }
          ]
        }
        """.trimIndent()

    private fun requestJson(
        attemptId: String,
        outcome: String = "CORRECT",
        occurredAt: String = "2026-09-28T09:00:00Z",
        deviceId: String = "device-a",
        lessonId: String = "550e8400-e29b-41d4-a716-446655440000",
    ): String =
        """
        {
          "deviceId": "PLACEHOLDER_DEVICE",
          "attempts": [
            {
              "attemptId": "PLACEHOLDER_ATTEMPT",
              "lessonId": "PLACEHOLDER_LESSON",
              "exerciseId": "exercise-1",
              "response": {"type": "TEXT", "value": "hello"},
              "outcome": "PLACEHOLDER_OUTCOME",
              "occurredAt": "PLACEHOLDER_OCCURRED_AT"
            }
          ]
        }
        """.trimIndent()
            .replace("PLACEHOLDER_DEVICE", deviceId)
            .replace("PLACEHOLDER_ATTEMPT", attemptId)
            .replace("PLACEHOLDER_LESSON", lessonId)
            .replace("PLACEHOLDER_OUTCOME", outcome)
            .replace("PLACEHOLDER_OCCURRED_AT", occurredAt)
}
