package com.kenlikdev.tildash

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.kenlikdev.tildash.learning.client.KtorLearnerContentTransport
import com.kenlikdev.tildash.learning.client.LearnerContentApplication
import com.kenlikdev.tildash.learning.client.createLearnerLessonApplication
import com.kenlikdev.tildash.storage.AccessTokenProvider
import com.kenlikdev.tildash.storage.SqlDelightDownloadedLessonStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

fun main() {
    val databaseDriver = createTildashDatabaseDriver()
    val learnerApplication = createLearnerLessonApplication(databaseDriver)
    val httpClient = HttpClient(CIO)

    val contentApplication =
        LearnerContentApplication(
            transport =
                KtorLearnerContentTransport(
                    client = httpClient,
                    baseUrl =
                        System.getenv("TILDASH_API_URL")
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                            ?: "http://127.0.0.1:8080",
                    accessTokenProvider =
                        AccessTokenProvider {
                            System.getenv("TILDASH_ACCESS_TOKEN")
                                ?.trim()
                                ?.takeIf { it.isNotBlank() }
                        },
                    developmentRole =
                        System.getenv("TILDASH_DEVELOPMENT_ROLE")
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                            ?: "learner",
                ),
            downloadedLessonStore = SqlDelightDownloadedLessonStore(databaseDriver),
        )

    application {
        Window(
            onCloseRequest = {
                httpClient.close()
                databaseDriver.close()
                exitApplication()
            },
            title = "Tildash",
        ) {
            App(
                learnerApplication = learnerApplication,
                contentApplication = contentApplication,
            )
        }
    }
}
