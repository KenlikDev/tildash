package com.kenlikdev.tildash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kenlikdev.tildash.learning.client.LearnerLessonApplication
import com.kenlikdev.tildash.learning.client.LearnerLessonScreen
import com.kenlikdev.tildash.learning.client.LearnerLessonState

@Composable
fun App(learnerApplication: LearnerLessonApplication? = null) {
    MaterialTheme {
        if (learnerApplication == null) {
            EmptyApplicationState()
        } else {
            LearnerApplicationContent(learnerApplication)
        }
    }
}

@Composable
private fun LearnerApplicationContent(learnerApplication: LearnerLessonApplication) {
    var learnerLessonState by
        remember(learnerApplication) {
            mutableStateOf<LearnerLessonState?>(null)
        }
    val downloadedLessons =
        remember(learnerApplication) {
            learnerApplication.listDownloadedLessons()
        }

    if (learnerLessonState == null) {
        if (downloadedLessons.isEmpty()) {
            EmptyDownloadedLessonsState()
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = downloadedLessons,
                    key = { it.lesson.id.value },
                ) { downloadedLesson ->
                    Button(
                        onClick = {
                            learnerLessonState =
                                learnerApplication.openLesson(downloadedLesson.lesson.id)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = downloadedLesson.lesson.title,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = downloadedLesson.course.title,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    } else {
        val currentLessonState = checkNotNull(learnerLessonState)
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { learnerLessonState = null },
            ) {
                Text("Back to lessons")
            }

            LearnerLessonScreen(
                state = currentLessonState,
                onSubmitAnswer = { value ->
                    learnerLessonState =
                        learnerApplication.submitText(
                            state = currentLessonState,
                            value = value,
                        )
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun EmptyDownloadedLessonsState() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "No downloaded lessons available.",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Download a published lesson before starting offline learning.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun EmptyApplicationState() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Learner storage is not configured for this platform build.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
