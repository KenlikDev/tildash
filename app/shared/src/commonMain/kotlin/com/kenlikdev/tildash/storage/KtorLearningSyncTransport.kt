package com.kenlikdev.tildash.storage

import com.kenlikdev.tildash.learning.LearningAttempt
import com.kenlikdev.tildash.learning.LearningProgressSyncBatch
import com.kenlikdev.tildash.learning.LearnerResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.bodyAsText
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

fun interface AccessTokenProvider {
    suspend fun accessToken(): String?
}

class AuthenticationRequiredLearningSyncFailure(
    message: String = "An authenticated access token is required for learning synchronization.",
) : Exception(message)

class AuthorizationDeniedLearningSyncFailure(
    message: String = "The authenticated identity is not authorized to synchronize learning progress.",
) : Exception(message)

class LearningSyncProtocolFailure(
    val statusCode: Int,
    message: String,
) : Exception(message)

class KtorLearningSyncTransport(
    private val client: HttpClient,
    baseUrl: String,
    private val accessTokenProvider: AccessTokenProvider,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : LearningSyncTransport {
    private val syncUrl = "${baseUrl.trimEnd('/')}/api/v1/learning/sync"

    init {
        require(baseUrl.isNotBlank()) {
            "Learning sync base URL must not be blank."
        }
    }

    override suspend fun synchronize(
        batch: LearningProgressSyncBatch,
    ): LearningSyncTransportResult {
        val accessToken =
            accessTokenProvider.accessToken()?.trim()
                ?: throw AuthenticationRequiredLearningSyncFailure()

        if (accessToken.isBlank()) {
            throw AuthenticationRequiredLearningSyncFailure()
        }

        val response =
            try {
                client.post(syncUrl) {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                    header(HttpHeaders.Accept, ContentType.Application.Json)
                    contentType(ContentType.Application.Json)
                    setBody(encodeRequest(batch).toString())
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (timeout: HttpRequestTimeoutException) {
                throw TransientLearningSyncFailure(
                    message = "The learning synchronization request timed out.",
                    cause = timeout,
                )
            } catch (failure: ResponseException) {
                throw mapResponseException(failure)
            } catch (failure: Exception) {
                throw TransientLearningSyncFailure(
                    message = "The learning synchronization request failed before a response was received.",
                    cause = failure,
                )
            }

        return decodeResponse(response)
    }

    private suspend fun decodeResponse(response: HttpResponse): LearningSyncTransportResult {
        val payload = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw mapHttpFailure(
                statusCode = response.status.value,
                detail = extractProblemDetail(payload),
            )
        }

        val objectPayload =
            runCatching { json.parseToJsonElement(payload).jsonObject }
                .getOrElse {
                    throw LearningSyncProtocolFailure(
                        response.status.value,
                        "Learning synchronization returned malformed JSON.",
                    )
                }

        val acknowledged = objectPayload.requiredStringArray("acknowledgedAttemptIds")
        val conflicts = objectPayload.requiredStringArray("conflictAttemptIds")

        return LearningSyncTransportResult.Succeeded(
            acknowledgedAttemptIds = acknowledged,
            conflictAttemptIds = conflicts,
        )
    }

    private fun mapResponseException(
        failure: ResponseException,
    ): Exception =
        mapHttpFailure(
            statusCode = failure.response.status.value,
            detail = null,
        )

    private fun mapHttpFailure(
        statusCode: Int,
        detail: String?,
    ): Exception {
        val message = detail?.takeIf(String::isNotBlank)

        return when (statusCode) {
            401 ->
                AuthenticationRequiredLearningSyncFailure(
                    message = message ?: "The learning synchronization access token was rejected.",
                )

            403 ->
                AuthorizationDeniedLearningSyncFailure(
                    message = message ?: "The authenticated identity is not authorized to synchronize learning progress.",
                )

            408,
            429,
            in 500..599 ->
                TransientLearningSyncFailure(
                    message = message ?: "The learning synchronization service is temporarily unavailable.",
                )

            else ->
                LearningSyncProtocolFailure(
                    statusCode = statusCode,
                    message = message ?: "The learning synchronization request was rejected with HTTP $statusCode.",
                )
        }
    }

    private fun extractProblemDetail(payload: String): String? =
        runCatching {
            json.parseToJsonElement(payload)
                .jsonObject
                .get("detail")
                ?.jsonPrimitive
                ?.contentOrNull
        }.getOrNull()

    private fun encodeRequest(
        batch: LearningProgressSyncBatch,
    ): JsonObject =
        buildJsonObject {
            put("deviceId", batch.deviceId)
            put(
                "attempts",
                buildJsonArray {
                    batch.attempts.forEach { attempt ->
                        add(encodeAttempt(attempt))
                    }
                },
            )
        }

    private fun encodeAttempt(
        attempt: LearningAttempt,
    ): JsonObject =
        buildJsonObject {
            put("attemptId", attempt.attemptId)
            put("exerciseId", attempt.exerciseId)
            put(
                "response",
                when (val response = attempt.response) {
                    is LearnerResponse.Text ->
                        buildJsonObject {
                            put("type", "TEXT")
                            put("value", response.value)
                        }
                },
            )
            put("outcome", attempt.outcome.name)
            put("occurredAt", attempt.occurredAt.toString())
        }

    private fun JsonObject.requiredStringArray(
        fieldName: String,
    ): List<String> {
        val value = get(fieldName)
        if (value !is JsonArray) {
            throw LearningSyncProtocolFailure(
                statusCode = 200,
                message = "Learning synchronization response is missing a string array: $fieldName.",
            )
        }

        return value.mapIndexed { index, element ->
            val primitive =
                element as? JsonPrimitive
                    ?: throw LearningSyncProtocolFailure(
                        200,
                        "Learning synchronization response contains a non-string value at $fieldName[$index].",
                    )

            primitive.contentOrNull
                ?: throw LearningSyncProtocolFailure(
                    200,
                    "Learning synchronization response contains a non-string value at $fieldName[$index].",
                )
        }
    }
}
