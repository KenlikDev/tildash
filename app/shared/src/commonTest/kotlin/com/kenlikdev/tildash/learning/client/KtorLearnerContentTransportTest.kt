package com.kenlikdev.tildash.learning.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KtorLearnerContentTransportTest {
    @Test
    fun catalogResponseIsMappedAndDevelopmentRoleIsSent() =
        runTest {
            lateinit var request: HttpRequestData
            val client =
                HttpClient(
                    MockEngine { requestData ->
                        request = requestData
                        respond(
                            content =
                                """
                                {
                                  "courses": [
                                    {
                                      "id": "550e8400-e29b-41d4-a716-446655440000",
                                      "title": "Basic Crimean Tatar",
                                      "sourceLocale": "crh",
                                      "publishedVersion": 1,
                                      "lessons": [
                                        {
                                          "id": "550e8400-e29b-41d4-a716-446655440001",
                                          "title": "Greetings",
                                          "sourceLocale": "crh",
                                          "publishedVersion": 2,
                                          "localizations": [
                                            {"locale": "ru", "value": "Приветствия"}
                                          ]
                                        }
                                      ]
                                    }
                                  ]
                                }
                                """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    },
                )

            try {
                val transport =
                    KtorLearnerContentTransport(
                        client = client,
                        baseUrl = "http://127.0.0.1:8080/",
                        accessTokenProvider = AccessTokenProvider { " token " },
                        developmentRole = "learner",
                    )

                val catalog = transport.loadCatalog()

                assertEquals("Basic Crimean Tatar", catalog.courses.single().title)
                assertEquals("Greetings", catalog.courses.single().lessons.single().title)
                assertEquals("Приветствия", catalog.courses.single().lessons.single().localizations.single().value)
                assertEquals(HttpMethod.Get, request.method)
                assertEquals(
                    "http://127.0.0.1:8080/api/v1/learning/catalog",
                    request.url.toString(),
                )
                assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
                assertEquals("learner", request.headers["X-Tildash-Development-Role"])
            } finally {
                client.close()
            }
        }

    @Test
    fun lessonPackageResponseIsMappedToExecutablePlan() =
        runTest {
            val client =
                HttpClient(
                    MockEngine {
                        respond(
                            content =
                                """
                                {
                                  "course": {
                                    "id": "550e8400-e29b-41d4-a716-446655440000",
                                    "title": "Basic Crimean Tatar",
                                    "sourceLocale": "crh",
                                    "publishedVersion": 1,
                                    "lessons": [
                                      {
                                        "id": "550e8400-e29b-41d4-a716-446655440001",
                                        "title": "Greetings",
                                        "sourceLocale": "crh",
                                        "publishedVersion": 1,
                                        "localizations": []
                                      }
                                    ]
                                  },
                                  "lesson": {
                                    "id": "550e8400-e29b-41d4-a716-446655440001",
                                    "title": "Greetings",
                                    "sourceLocale": "crh",
                                    "publishedVersion": 1,
                                    "localizations": []
                                  },
                                  "exercises": [
                                    {
                                      "id": "exercise-1",
                                      "contentId": "550e8400-e29b-41d4-a716-446655440001",
                                      "type": "MANUAL_INPUT",
                                      "prompt": "Translate hello.",
                                      "expectedAnswers": ["merhaba"]
                                    }
                                  ]
                                }
                                """.trimIndent(),
                            status = HttpStatusCode.OK,
                        )
                    },
                )

            try {
                val transport =
                    KtorLearnerContentTransport(
                        client = client,
                        baseUrl = "http://example.test",
                    )
                val packageData =
                    transport.loadLesson(
                        com.kenlikdev.tildash.content.model.ContentId(
                            "550e8400-e29b-41d4-a716-446655440001",
                        ),
                    )

                assertEquals("Greetings", packageData.lesson.title)
                assertEquals(1, packageData.plan.exercises.size)
                assertEquals("Translate hello.", packageData.plan.exercises.single().prompt)
            } finally {
                client.close()
            }
        }

    @Test
    fun httpFailureExposesStatusAndProblemDetail() =
        runTest {
            val client =
                HttpClient(
                    MockEngine {
                        respond(
                            content =
                                """{"type":"urn:tildash:problem:unauthorized","title":"Unauthorized","detail":"Authentication is required."}""",
                            status = HttpStatusCode.Unauthorized,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.ProblemJson.toString()),
                        )
                    },
                )

            try {
                val transport =
                    KtorLearnerContentTransport(
                        client = client,
                        baseUrl = "http://example.test",
                    )

                val failure =
                    assertFailsWith<LearnerContentTransportFailure> {
                        transport.loadCatalog()
                    }

                assertEquals(401, failure.statusCode)
                assertEquals("Authentication is required.", failure.message)
            } finally {
                client.close()
            }
        }

    @Test
    fun cancellationIsNotConvertedToTransportFailure() =
        runTest {
            val client =
                HttpClient(
                    MockEngine {
                        throw kotlin.coroutines.cancellation.CancellationException("cancelled")
                    },
                )

            try {
                val transport =
                    KtorLearnerContentTransport(
                        client = client,
                        baseUrl = "http://example.test",
                    )

                assertFailsWith<kotlin.coroutines.cancellation.CancellationException> {
                    transport.loadCatalog()
                }
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
