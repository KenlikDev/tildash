@file:Suppress("ktlint:standard:function-naming")

package com.kenlikdev.tildash

import androidx.compose.ui.window.ComposeUIViewController
import com.kenlikdev.tildash.learning.client.createLearnerLessonApplication

fun MainViewController() =
    ComposeUIViewController {
        val learnerApplication =
            createLearnerLessonApplication(
                createTildashDatabaseDriver(),
            )
        App(learnerApplication)
    }
