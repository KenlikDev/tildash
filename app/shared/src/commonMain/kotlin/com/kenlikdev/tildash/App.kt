package com.kenlikdev.tildash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kenlikdev.tildash.learning.client.LearnerLessonScreen
import com.kenlikdev.tildash.learning.client.LearnerLessonState

@Composable
fun App(
    learnerLessonState: LearnerLessonState? = null,
    onSubmitAnswer: (String) -> Unit = {},
) {
    MaterialTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (learnerLessonState == null) {
                Text(
                    text = "Select a lesson to begin.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                LearnerLessonScreen(
                    state = learnerLessonState,
                    onSubmitAnswer = onSubmitAnswer,
                )
            }
        }
    }
}
