@file:Suppress("ktlint:standard:function-naming")

package com.kenlikdev.tildash

import androidx.compose.ui.window.ComposeUIViewController
import com.kenlikdev.tildash.learning.client.createLearnerLessonApplication
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    val learnerApplication =
        createLearnerLessonApplication(
            createTildashDatabaseDriver(),
        )

    return ComposeUIViewController {
        App(learnerApplication)
    }
}
