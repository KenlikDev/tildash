package com.kenlikdev.tildash.learning.client

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kenlikdev.tildash.content.model.ContentKind
import com.kenlikdev.tildash.content.model.ContentPayload
import com.kenlikdev.tildash.learning.AnswerOutcome
import com.kenlikdev.tildash.learning.LessonSessionState

@Composable
fun LearnerLessonScreen(
    state: LearnerLessonState,
    onSubmitAnswer: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var answer by remember(state.lesson.lesson.id) { mutableStateOf("") }

    LearnerLessonContent(
        state = state,
        answer = answer,
        onAnswerChanged = { answer = it },
        onSubmitAnswer = {
            onSubmitAnswer(answer)
            answer = ""
        },
        modifier = modifier,
    )
}

@Composable
private fun LearnerLessonContent(
    state: LearnerLessonState,
    answer: String,
    onAnswerChanged: (String) -> Unit,
    onSubmitAnswer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalExercises = state.session.plan.exercises.size
    val completedExerciseIds =
        state.session.progress.attempts
            .asSequence()
            .filter { it.lessonId == state.session.plan.lessonId }
            .filter { it.publishedVersion == state.session.plan.publishedVersion }
            .filter { it.outcome == AnswerOutcome.CORRECT }
            .map { it.exerciseId }
            .toSet()
    val completedExercises =
        state.session.plan.exercises
            .count { it.id in completedExerciseIds }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = state.lesson.lesson.title,
            style = MaterialTheme.typography.headlineMedium,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "$completedExercises / $totalExercises",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text =
                    when (state.session.state) {
                        LessonSessionState.ACTIVE -> "In progress"
                        LessonSessionState.COMPLETED -> "Completed"
                    },
                style = MaterialTheme.typography.labelLarge,
            )
        }

        LinearProgressIndicator(
            progress = {
                completedExercises.toFloat() / totalExercises.toFloat()
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.lesson.materials.isNotEmpty()) {
            Text("Study material", style = MaterialTheme.typography.titleLarge)
            state.lesson.materials.sortedBy { it.position }.forEach { material ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when (material.kind) {
                        ContentKind.EXPLANATION -> {
                            Text("Learn", style = MaterialTheme.typography.labelLarge)
                            Text((material.payload as ContentPayload.Text).value)
                        }

                        ContentKind.EXAMPLE -> {
                            Text("Example", style = MaterialTheme.typography.labelLarge)
                            Text((material.payload as ContentPayload.Text).value)
                        }

                        ContentKind.VOCABULARY -> {
                            val vocabulary = material.payload as ContentPayload.Vocabulary
                            Text(vocabulary.term, style = MaterialTheme.typography.titleMedium)
                            Text(vocabulary.gloss, style = MaterialTheme.typography.bodyLarge)
                        }

                        else -> error("Unsupported learner lesson material kind: " + material.kind)
                    }
                }
            }
        }

        when (state.session.state) {
            LessonSessionState.COMPLETED -> {
                Text(
                    text = "Lesson complete.",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }

            LessonSessionState.ACTIVE -> {
                val exercise = checkNotNull(state.session.nextExercise)

                Text("Practice", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = exercise.prompt,
                    style = MaterialTheme.typography.headlineSmall,
                )

                OutlinedTextField(
                    value = answer,
                    onValueChange = onAnswerChanged,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("learner-answer-input"),
                    label = { Text("Your answer") },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            imeAction = ImeAction.Done,
                        ),
                )

                Button(
                    onClick = onSubmitAnswer,
                    enabled = answer.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Check answer")
                }

                Spacer(modifier = Modifier.height(4.dp))

                state.lastEvaluation?.let { evaluation ->
                    val message =
                        when (evaluation.outcome) {
                            AnswerOutcome.CORRECT -> "Correct"
                            AnswerOutcome.INCORRECT -> "Try again"
                        }
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
