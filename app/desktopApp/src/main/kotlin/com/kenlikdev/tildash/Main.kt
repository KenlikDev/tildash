package com.kenlikdev.tildash

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.kenlikdev.tildash.learning.client.createLearnerLessonApplication

fun main() {
    val learnerApplication =
        createLearnerLessonApplication(
            createTildashDatabaseDriver(),
        )

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Tildash",
        ) {
            App(learnerApplication)
        }
    }
}
