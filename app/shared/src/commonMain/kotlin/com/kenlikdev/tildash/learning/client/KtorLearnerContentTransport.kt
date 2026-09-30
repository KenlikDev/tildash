package com.kenlikdev.tildash.learning.client

import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.content.model.LanguageTag
import com.kenlikdev.tildash.learning.LearnerCourseCatalog
import com.kenlikdev.tildash.learning.LearnerCourseSummary
import com.kenlikdev.tildash.learning.LearnerLessonPackage
import com.kenlikdev.tildash.learning.LearnerLessonSummary
import com.kenlikdev.tildash.learning.LearnerLocalizedText
import com.kenlikdev.tildash.learning.LearningPlan
import com.kenlikdev.tildash.learning.ManualInputExercise
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpHeaders
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LearnerContentTransportFailure(
    val statusCode: Int?,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class KtorLearnerContentTransport(
    private val client: HttpClient,
    baseUrl: String,
    private val accessTokenProvider: AccessTokenProvider? = null,
    private val developmentRole: String? = null,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : LearnerContentTransport {
    private val baseUrl = baseUrl.trimEnd('/')

    init {
        require(this.baseUrl.isNotBlank()) {
            "Learner content base URL must not be blank."
        }
    }

    override suspend fun loadCatalog(): LearnerCourseCatalog =
        getJson("/api/v1/learning/catalog").toCatalog()

    override suspend fun loadLesson(lessonId: ContentId): LearnerLessonPackage =
        getJson("/api/v1/learning/lessons/" + lessonId.value).toLessonPackage()

    private suspend fun getJson(path: String): JsonObject {
        val response =
            try {
                client.get(baseUrl + path) {
                    header(HttpHeaders.Accept, ContentType.Application.Json)
                    accessTokenProvider?.accessToken()?.trim()?.takeIf { it.isNotBlank() }?.let { token ->
                        header(HttpHeaders.Authorization, "Bearer " + token)
                    }
                    developmentRole?.trim()?.takeIf { it.isNotBlank() }?.let { role ->
                        header("X-Tildash-Development-Role", role)
                    }
                }
            } catch (timeout: HttpRequestTimeoutException) {
                throw LearnerContentTransportFailure(
                    statusCode = null,
                    message = "The learner content request timed out.",
                    cause = timeout,
                )
            } catch (failure: Exception) {
                throw LearnerContentTransportFailure(
                    statusCode = null,
                    message = "The learner content request failed before a response was received.",
                    cause = failure,
                )
            }

        val payload = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw LearnerContentTransportFailure(
                statusCode = response.status.value,
                message =
                    parseProblemDetail(payload)
                        ?: "Learner content request failed with HTTP " + response.status.value + ".",
            )
        }

        return runCatching {
            json.parseToJsonElement(payload).jsonObject
        }.getOrElse { failure ->
            throw LearnerContentTransportFailure(
                statusCode = response.status.value,
                message = "Learner content response is not a JSON object.",
                cause = failure,
            )
        }
    }

    private fun JsonObject.toCatalog(): LearnerCourseCatalog =
        LearnerCourseCatalog(
            courses =
                requiredArray("courses").map { it.jsonObject.toCourseSummary() },
        )

    private fun JsonObject.toCourseSummary(): LearnerCourseSummary =
        LearnerCourseSummary(
            id = ContentId(requiredString("id")),
            title = requiredString("title"),
            sourceLocale = LanguageTag(requiredString("sourceLocale")),
            publishedVersion = requiredInt("publishedVersion"),
            lessons =
                requiredArray("lessons").map { it.jsonObject.toLessonSummary() },
        )

    private fun JsonObject.toLessonSummary(): LearnerLessonSummary =
        LearnerLessonSummary(
            id = ContentId(requiredString("id")),
            title = requiredString("title"),
            sourceLocale = LanguageTag(requiredString("sourceLocale")),
            publishedVersion = requiredInt("publishedVersion"),
            localizations =
                optionalArray("localizations").map { localization ->
                    localization.jsonObject.let {
                        LearnerLocalizedText(
                            locale = LanguageTag(it.requiredString("locale")),
                            value = it.requiredString("value"),
                        )
                    }
                },
        )

    private fun JsonObject.toLessonPackage(): LearnerLessonPackage {
        val course = requiredObject("course").toCourseSummary()
        val lesson = requiredObject("lesson").toLessonSummary()
        val exercises =
            requiredArray("exercises").map { element ->
                val exercise = element.jsonObject
                when (exercise.requiredString("type")) {
                    "MANUAL_INPUT" -> {
                        ManualInputExercise(
                            id = exercise.requiredString("id"),
                            contentId = ContentId(exercise.requiredString("contentId")),
                            prompt = exercise.requiredString("prompt"),
                            expectedAnswers =
                                exercise.requiredArray("expectedAnswers").map { answer ->
                                    answer.jsonPrimitive.content
                                },
                        )
                    }

                    else -> {
                        throw LearnerContentTransportFailure(
                            statusCode = null,
                            message =
                                "Unsupported learner exercise type: " +
                                    exercise.requiredString("type") +
                                    ".",
                        )
                    }
                }
            }

        return LearnerLessonPackage(
            course = course,
            lesson = lesson,
            plan = LearningPlan(
                lessonId = lesson.id,
                exercises = exercises,
            ),
        )
    }

    private fun JsonObject.requiredString(name: String): String =
        get(name)?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: throw LearnerContentTransportFailure(
                statusCode = null,
                message = "Learner content response is missing a non-blank '$name'.",
            )

    private fun JsonObject.requiredInt(name: String): Int =
        requiredString(name).toIntOrNull()
            ?: throw LearnerContentTransportFailure(
                statusCode = null,
                message = "Learner content response field '$name' is not an integer.",
            )

    private fun JsonObject.requiredArray(name: String): JsonArray =
        get(name)?.jsonArray
            ?: throw LearnerContentTransportFailure(
                statusCode = null,
                message = "Learner content response is missing array '$name'.",
            )

    private fun JsonObject.optionalArray(name: String): JsonArray = get(name)?.jsonArray ?: JsonArray(emptyList())

    private fun JsonObject.requiredObject(name: String): JsonObject =
        get(name)?.jsonObject
            ?: throw LearnerContentTransportFailure(
                statusCode = null,
                message = "Learner content response is missing object '$name'.",
            )

    private fun parseProblemDetail(payload: String): String? =
        runCatching {
            json.parseToJsonElement(payload)
                .jsonObject["detail"]
                ?.jsonPrimitive
                ?.content
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
}
