package com.kenlikdev.tildash.server

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

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
            )
            .andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("urn:tildash:problem:unauthorized"))
    }

    @Test
    fun newAttemptIsAcknowledgedAndPersisted() {
        mockMvc
            .perform(
                post("/api/v1/learning/sync")
                    .with(user("learner-a").roles("LEARNER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson("attempt-new")),
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.acknowledgedAttemptIds[0]").value("attempt-new"))
            .andExpect(jsonPath("$.conflictAttemptIds").isEmpty)

        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                "select count(*) from tildash.learning_attempts where learner_subject = 'learner-a' and attempt_id = 'attempt-new'",
                Int::class.java,
            ),
        )
    }

    @Test
    fun identicalRetryIsAcknowledgedWithoutDuplicateRow() {
        val request = requestJson("attempt-idempotent")

        repeat(2) {
            mockMvc
                .perform(
                    post("/api/v1/learning/sync")
                        .with(user("learner-a").roles("LEARNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request),
                )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.acknowledgedAttemptIds[0]").value("attempt-idempotent"))
                .andExpect(jsonPath("$.conflictAttemptIds").isEmpty)
        }

        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                "select count(*) from tildash.learning_attempts where learner_subject = 'learner-a' and attempt_id = 'attempt-idempotent'",
                Int::class.java,
            ),
        )
    }

    @Test
    fun conflictingRetryIsReportedAndExistingPayloadIsPreserved() {
        mockMvc
            .perform(
                post("/api/v1/learning/sync")
                    .with(user("learner-a").roles("LEARNER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson("attempt-conflict", "CORRECT", "2026-09-28T09:00:00Z")),
            )
            .andExpect(status().isOk)

        mockMvc
            .perform(
                post("/api/v1/learning/sync")
                    .with(user("learner-a").roles("LEARNER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson("attempt-conflict", "INCORRECT", "2026-09-28T10:00:00Z")),
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.acknowledgedAttemptIds").isEmpty)
            .andExpect(jsonPath("$.conflictAttemptIds[0]").value("attempt-conflict"))

        assertEquals(
            "CORRECT",
            jdbcTemplate.queryForObject(
                "select outcome from tildash.learning_attempts where learner_subject = 'learner-a' and attempt_id = 'attempt-conflict'",
                String::class.java,
            ),
        )
    }

    @Test
    fun contradictoryDuplicateAttemptIdsInOneBatchAreReportedAsConflict() {
        mockMvc
            .perform(
                post("/api/v1/learning/sync")
                    .with(user("learner-a").roles("LEARNER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "deviceId": "device-a",
                          "attempts": [
                            {
                              "attemptId": "attempt-duplicate",
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
                        """.trimIndent(),
                    ),
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.acknowledgedAttemptIds").isEmpty)
            .andExpect(jsonPath("$.conflictAttemptIds[0]").value("attempt-duplicate"))

        assertEquals(
            0,
            jdbcTemplate.queryForObject(
                "select count(*) from tildash.learning_attempts where learner_subject = 'learner-a' and attempt_id = 'attempt-duplicate'",
                Int::class.java,
            ),
        )
    }

    @Test
    fun sameAttemptIdIsScopedToAuthenticatedLearner() {
        val request = requestJson("attempt-shared-id")

        mockMvc
            .perform(
                post("/api/v1/learning/sync")
                    .with(user("learner-a").roles("LEARNER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request),
            )
            .andExpect(status().isOk)

        mockMvc
            .perform(
                post("/api/v1/learning/sync")
                    .with(user("learner-b").roles("LEARNER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request),
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.acknowledgedAttemptIds[0]").value("attempt-shared-id"))

        assertEquals(
            2,
            jdbcTemplate.queryForObject(
                "select count(*) from tildash.learning_attempts where attempt_id = 'attempt-shared-id'",
                Int::class.java,
            ),
        )
    }

    @Test
    fun persistedLearningAttemptCannotBeUpdatedOrDeleted() {
        mockMvc
            .perform(
                post("/api/v1/learning/sync")
                    .with(user("learner-a").roles("LEARNER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson("attempt-immutable")),
            )
            .andExpect(status().isOk)

        val updateFailure =
            org.junit.jupiter.api.assertThrows<org.springframework.dao.DataAccessException> {
                jdbcTemplate.update(
                    "update tildash.learning_attempts set outcome = 'INCORRECT' where learner_subject = 'learner-a' and attempt_id = 'attempt-immutable'",
                )
            }
        assertTrue(updateFailure.message?.contains("Learning attempt history is immutable") == true)

        val deleteFailure =
            org.junit.jupiter.api.assertThrows<org.springframework.dao.DataAccessException> {
                jdbcTemplate.update(
                    "delete from tildash.learning_attempts where learner_subject = 'learner-a' and attempt_id = 'attempt-immutable'",
                )
            }
        assertTrue(deleteFailure.message?.contains("Learning attempt history is immutable") == true)

        assertEquals(
            "CORRECT",
            jdbcTemplate.queryForObject(
                "select outcome from tildash.learning_attempts where learner_subject = 'learner-a' and attempt_id = 'attempt-immutable'",
                String::class.java,
            ),
        )
    }

    @Test
    fun invalidDeviceIdUsesProblemDetails() {
        mockMvc
            .perform(
                post("/api/v1/learning/sync")
                    .with(user("learner-a").roles("LEARNER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson("attempt-invalid-device", deviceId = " ")),
            )
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
    }

    private fun requestJson(
        attemptId: String,
        outcome: String = "CORRECT",
        occurredAt: String = "2026-09-28T09:00:00Z",
        deviceId: String = "device-a",
    ): String =
        """
        {
          "deviceId": "$deviceId",
          "attempts": [
            {
              "attemptId": "$attemptId",
              "exerciseId": "exercise-1",
              "response": {
                "type": "TEXT",
                "value": "hello"
              },
              "outcome": "$outcome",
              "occurredAt": "$occurredAt"
            }
          ]
        }
        """.trimIndent()
}
