package com.kenlikdev.tildash.learning.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertIs

class KtorLearnerContentTransportCancellationTest {
    @Test
    fun cancellationIsPropagatedInsteadOfConvertedToTransportFailure() = runBlocking {
        val client =
            HttpClient(
                MockEngine {
                    throw CancellationException("cancelled")
                },
            )

        try {
            val failure =
                try {
                    KtorLearnerContentTransport(client, "http://example.test").loadCatalog()
                    null
                } catch (caught: Throwable) {
                    caught
                }

            assertIs<CancellationException>(failure)
            Unit
        } finally {
            client.close()
        }
    }
}
