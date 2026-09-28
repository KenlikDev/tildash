package com.kenlikdev.tildash.storage

import com.kenlikdev.tildash.learning.AnswerOutcome
import com.kenlikdev.tildash.learning.LearnerResponse
import com.kenlikdev.tildash.learning.LearningAttempt
import com.kenlikdev.tildash.learning.LearningProgressSyncBatch
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.assertTrue
import kotlin.time.Instant

class KtorLearningSyncTransportTest {
    private val attempt =
        LearningAttempt(
            attemptId = "attempt-1",
            exerciseId = "exercise-1",
            response = LearnerResponse.Text("hello"),
            outcome = AnswerOutcome.CORRECT,
            occurredAt = Instant.parse("2026-09-28T08:00:00Z"),
        )

    private val batch =
        LearningProgressSyncBatch(
            deviceId = "device-1",
            attempts = listOf(attempt),
        )

    @Test
    fun successfulResponseUsesAuthenticatedSyncEndpoint() =
        runTest {
            lateinit var request: HttpRequestData

            val client =
                HttpClient(
                    MockEngine {
                        requestData ->
                            request = requestData
                            respond(
                                content = """{"acknowledgedAttemptIds":["attempt-1"],"conflictAttemptIds":[]}""",
                                status = HttpStatusCode.OK,
                                headers =
                                    io.ktor.http.headersOf(
                                        HttpHeaders.ContentType,
                                        ContentType.Application.Json.toString(),
                                    ),
                            )
                    },
                )

            try {
                val transport =
                    KtorLearningSyncTransport(
                        client = client,
                        baseUrl = "https://example.test/",
                        accessTokenProvider = AccessTokenProvider { "  token-123  " },
                    )

                val result = transport.synchronize(batch)

                assertEquals(
                    LearningSyncTransportResult.Succeeded(
                        acknowledgedAttemptIds = listOf("attempt-1"),
                        conflictAttemptIds = emptyList(),
                    ),
                    result,
                )
                assertEquals(
                    "https://example.test/api/v1/learning/sync",
                    request.url.toString(),
                )
                assertEquals(
                    "Bearer token-123",
                    request.headers[HttpHeaders.Authorization],
                )
                assertEquals(
                    ContentType.Application.Json.toString(),
                    request.headers[HttpHeaders.ContentType],
                )
                val body = request.body as TextContent
                assertTrue(body.text.contains("deviceId\":\"device-1"))
                assertTrue(body.text.contains("attemptId\":\"attempt-1"))
                assertTrue(body.text.contains("exerciseId\":\"exercise-1"))
                assertTrue(body.text.contains("type\":\"TEXT"))
                assertTrue(body.text.contains("outcome\":\"CORRECT"))
                assertTrue(body.text.contains("occurredAt\":\"2026-09-28T08:00:00Z"))
            } finally {
                client.close()
            }
        }

    @Test
    fun missingTokenFailsBeforeNetworkRequest() =
        runTest {
            var requests = 0
            val client =
                HttpClient(
                    MockEngine {
                        requests += 1
                        respond("""{}""")
                    },
                )

            try {
                val transport =
                    KtorLearningSyncTransport(
                        client = client,
                        baseUrl = "https://example.test",
                        accessTokenProvider = AccessTokenProvider { null },
                    )

                assertFailsWith<AuthenticationRequiredLearningSyncFailure> {
                    transport.synchronize(batch)
                }
                assertEquals(0, requests)
            } finally {
                client.close()
            }
        }

    @Test
    fun unauthorizedResponseBecomesAuthenticationFailure() =
        runTest {
            val client =
                HttpClient(
                    MockEngine {
                        respond(
                            content = """{"detail":"Token expired"}""",
                            status = HttpStatusCode.Unauthorized,
                            headers =
                                io.ktor.http.headersOf(
                                    HttpHeaders.ContentType,
                                    ContentType.Application.ProblemJson.toString(),
                                ),
                        )
                    },
                )

            try {
                val transport =
                    KtorLearningSyncTransport(
                        client = client,
                        baseUrl = "https://example.test",
                        accessTokenProvider = AccessTokenProvider { "token" },
                    )

                val failure = assertFailsWith<AuthenticationRequiredLearningSyncFailure> {
                    transport.synchronize(batch)
                }

                assertEquals("Token expired", failure.message)
            } finally {
                client.close()
            }
        }

    @Test
    fun forbiddenResponseBecomesAuthorizationFailure() =
        runTest {
            val client =
                HttpClient(
                    MockEngine {
                        respond(
                            content = """{"detail":"Learner role required"}""",
                            status = HttpStatusCode.Forbidden,
                        )
                    },
                )

            try {
                val transport =
                    KtorLearningSyncTransport(
                        client = client,
                        baseUrl = "https://example.test",
                        accessTokenProvider = AccessTokenProvider { "token" },
                    )

                val failure = assertFailsWith<AuthorizationDeniedLearningSyncFailure> {
                    transport.synchronize(batch)
                }

                assertEquals("Learner role required", failure.message)
            } finally {
                client.close()
            }
        }

    @Test
    fun serverFailuresAreMarkedTransient() =
        runTest {
            val client =
                HttpClient(
                    MockEngine {
                        respond(
                            content = """{"detail":"Service unavailable"}""",
                            status = HttpStatusCode.ServiceUnavailable,
                        )
                    },
                )

            try {
                val transport =
                    KtorLearningSyncTransport(
                        client = client,
                        baseUrl = "https://example.test",
                        accessTokenProvider = AccessTokenProvider { "token" },
                    )

                val failure = assertFailsWith<TransientLearningSyncFailure> {
                    transport.synchronize(batch)
                }

                assertEquals("Service unavailable", failure.message)
            } finally {
                client.close()
            }
        }

    @Test
    fun malformedSuccessResponseFailsAsProtocolError() =
        runTest {
            val client =
                HttpClient(
                    MockEngine {
                        respond(
                            content = """{"acknowledgedAttemptIds":"attempt-1","conflictAttemptIds":[]}""",
                            status = HttpStatusCode.OK,
                        )
                    },
                )

            try {
                val transport =
                    KtorLearningSyncTransport(
                        client = client,
                        baseUrl = "https://example.test",
                        accessTokenProvider = AccessTokenProvider { "token" },
                    )

                val failure = assertFailsWith<LearningSyncProtocolFailure> {
                    transport.synchronize(batch)
                }

                assertEquals(200, failure.statusCode)
            } finally {
                client.close()
            }
        }

    @Test
    fun networkFailureBecomesTransient() =
        runTest {
            val client =
                HttpClient(
                    MockEngine {
                        throw IllegalStateException("connection refused")
                    },
                )

            try {
                val transport =
                    KtorLearningSyncTransport(
                        client = client,
                        baseUrl = "https://example.test",
                        accessTokenProvider = AccessTokenProvider { "token" },
                    )

                val failure = assertFailsWith<TransientLearningSyncFailure> {
                    transport.synchronize(batch)
                }

                assertTrue(failure.message?.contains("failed before a response") == true)
            } finally {
                client.close()
            }
        }
    private fun runTest(block: suspend () -> Unit) {
        var failure: Throwable? = null
        block.startCoroutine(
            object : Continuation<Unit> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(result: Result<Unit>) {
                    failure = result.exceptionOrNull()
                }
            },
        )
        failure?.let { throw it }
    }

}

