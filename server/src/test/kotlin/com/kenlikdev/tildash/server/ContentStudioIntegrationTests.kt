package com.kenlikdev.tildash.server

import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration::class)
class ContentStudioIntegrationTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun teacherCanCreatePreviewSubmitAndReviewerCanPublishLesson() {
        val courseId = createNode("COURSE", null, "Course")
        val lessonId = createNode("LESSON", courseId, "Lesson")

        mockMvc
            .perform(
                get("/api/v1/content/$lessonId/preview")
                    .with(user("teacher").roles("TEACHER"))
                    .accept(MediaType.APPLICATION_JSON),
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("DRAFT"))
            .andExpect(jsonPath("$.revision").value(1))
            .andExpect(jsonPath("$.payload.value").value("Lesson"))

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/submit")
                    .with(user("teacher").roles("TEACHER"))
                    .accept(MediaType.APPLICATION_JSON),
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("SUBMITTED"))

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/review/start")
                    .with(user("reviewer").roles("REVIEWER"))
                    .accept(MediaType.APPLICATION_JSON),
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("UNDER_REVIEW"))

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/review/feedback")
                    .with(user("reviewer").roles("REVIEWER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("reason" to "Improve the exercise wording."))),
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("UNDER_REVIEW"))

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/review/approve")
                    .with(user("reviewer").roles("REVIEWER"))
                    .accept(MediaType.APPLICATION_JSON),
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("APPROVED"))

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/publish")
                    .with(user("reviewer").roles("REVIEWER"))
                    .accept(MediaType.APPLICATION_JSON),
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("PUBLISHED"))

        mockMvc
            .perform(
                get("/api/v1/content/$lessonId/review-history")
                    .with(user("reviewer").roles("REVIEWER"))
                    .accept(MediaType.APPLICATION_JSON),
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(5))
            .andExpect(jsonPath("$[1].action").value("START_REVIEW"))
            .andExpect(jsonPath("$[2].action").value("FEEDBACK"))
            .andExpect(jsonPath("$[4].action").value("PUBLISH"))

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/archive")
                    .with(user("reviewer").roles("REVIEWER"))
                    .accept(MediaType.APPLICATION_JSON),
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("ARCHIVED"))
    }

    @Test
    fun teacherCannotPublishDirectly() {
        val courseId = createNode("COURSE", null, "Course")
        val lessonId = createNode("LESSON", courseId, "Lesson")

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/publish")
                    .with(user("teacher").roles("TEACHER"))
                    .accept(MediaType.APPLICATION_PROBLEM_JSON),
            )
            .andExpect(status().isForbidden)
    }

    @Test
    fun validationErrorsBlockSubmission() {
        val courseId = createNode("COURSE", null, "Course")
        val invalidParentId = createNode("EXAMPLE", courseId, "Invalid parent")
        val lessonId = createNode("LESSON", invalidParentId, "Lesson")

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/submit")
                    .with(user("teacher").roles("TEACHER"))
                    .accept(MediaType.APPLICATION_PROBLEM_JSON),
            )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.type").value("urn:tildash:problem:validation-failed"))
            .andExpect(jsonPath("$.errors[0].code").value("INVALID_LESSON_PARENT"))
            .andExpect(jsonPath("$.status").value(409))
    }

    @Test
    fun reviewerCanRejectWithAuditableReason() {
        val courseId = createNode("COURSE", null, "Course")
        val lessonId = createNode("LESSON", courseId, "Lesson")

        mockMvc.perform(
            post("/api/v1/content/$lessonId/submit")
                .with(user("teacher").roles("TEACHER")),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/content/$lessonId/review/start")
                .with(user("reviewer").roles("REVIEWER")),
        ).andExpect(status().isOk)

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/review/reject")
                    .with(user("reviewer").roles("REVIEWER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("reason" to "Fix the source citation."))),
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("DRAFT"))

        mockMvc
            .perform(
                get("/api/v1/content/$lessonId/review-history")
                    .with(user("teacher").roles("TEACHER"))
                    .accept(MediaType.APPLICATION_JSON),
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[2].action").value("REJECT"))
            .andExpect(jsonPath("$[2].reason").value("Fix the source citation."))
    }

    @Test
    fun publishedNodeAndWorkflowHistoryAreImmutableAtDatabaseBoundary() {
        val courseId = createNode("COURSE", null, "Course")
        val lessonId = createNode("LESSON", courseId, "Lesson")
        publishLesson(lessonId)

        assertFailsWith<DataAccessException> {
            jdbcTemplate.update(
                "update tildash.content_nodes set position = 9 where id = ?::uuid",
                lessonId,
            )
        }

        assertFailsWith<DataAccessException> {
            jdbcTemplate.update(
                "delete from tildash.content_workflow_events where content_node_id = ?::uuid",
                lessonId,
            )
        }
    }

    @Test
    fun draftCanBeEditedButPublishedContentCannot() {
        val courseId = createNode("COURSE", null, "Course")
        val lessonId = createNode("LESSON", courseId, "Lesson")

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/source-revisions")
                    .with(user("teacher").roles("TEACHER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            revisionRequest("Updated lesson"),
                        ),
                    ),
            )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.revision").value(2))

        publishLesson(lessonId)

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/source-revisions")
                    .with(user("teacher").roles("TEACHER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(revisionRequest("Illegal update"))),
            )
            .andExpect(status().isConflict)
    }

    private fun publishLesson(lessonId: String) {
        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/submit")
                    .with(user("teacher").roles("TEACHER")),
            )
            .andExpect(status().isOk)

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/review/start")
                    .with(user("reviewer").roles("REVIEWER")),
            )
            .andExpect(status().isOk)

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/review/approve")
                    .with(user("reviewer").roles("REVIEWER")),
            )
            .andExpect(status().isOk)

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/publish")
                    .with(user("reviewer").roles("REVIEWER")),
            )
            .andExpect(status().isOk)
    }

    private fun createNode(
        kind: String,
        parentId: String?,
        value: String,
        copyrightStatus: String = "LICENSED",
        includeLicense: Boolean = true,
    ): String {
        val result =
            mockMvc
                .perform(
                    post("/api/v1/content/nodes")
                        .with(user("teacher").roles("TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsString(
                                mapOf(
                                    "kind" to kind,
                                    "parentId" to parentId,
                                    "sourceLocale" to "crh",
                                    "position" to 0,
                                    "payload" to mapOf(
                                        "type" to "TEXT",
                                        "value" to value,
                                    ),
                                    "provenance" to
                                        mapOf(
                                            "sourceTitle" to "Test source",
                                            "copyrightStatus" to copyrightStatus,
                                            "licenseIdentifier" to if (includeLicense) "CC BY 4.0" else null,
                                            "authorName" to "Teacher",
                                        ),
                                ),
                            ),
                        ),
                )
                .andExpect(status().isCreated)
                .andReturn()

        val response = objectMapper.readTree(result.response.contentAsString)
        val id = response.get("id")?.asText()
        assertNotNull(id)
        return id
    }

    private fun revisionRequest(value: String) =
        mapOf(
            "payload" to mapOf("type" to "TEXT", "value" to value),
            "provenance" to
                mapOf(
                    "sourceTitle" to "Test source",
                    "copyrightStatus" to "LICENSED",
                    "licenseIdentifier" to "CC BY 4.0",
                    "authorName" to "Teacher",
                ),
        )
}
