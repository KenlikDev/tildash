package com.kenlikdev.tildash

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.content.model.ContentKind
import com.kenlikdev.tildash.content.model.ContentPayload
import com.kenlikdev.tildash.content.model.LanguageTag
import com.kenlikdev.tildash.learning.AnswerOutcome
import com.kenlikdev.tildash.learning.LearnerCourseCatalog
import com.kenlikdev.tildash.learning.LearnerCourseSummary
import com.kenlikdev.tildash.learning.LearnerLessonMaterial
import com.kenlikdev.tildash.learning.LearnerLessonPackage
import com.kenlikdev.tildash.learning.LearnerLessonSummary
import com.kenlikdev.tildash.learning.LearningAttempt
import com.kenlikdev.tildash.learning.LearningPlan
import com.kenlikdev.tildash.learning.LearningProgress
import com.kenlikdev.tildash.learning.ManualInputExercise
import com.kenlikdev.tildash.learning.client.LearnerContentApplication
import com.kenlikdev.tildash.learning.client.LearnerContentTransport
import com.kenlikdev.tildash.learning.client.LearnerLessonApplication
import com.kenlikdev.tildash.learning.client.LearnerLessonCoordinator
import com.kenlikdev.tildash.storage.DownloadedLesson
import com.kenlikdev.tildash.storage.DownloadedLessonStore
import com.kenlikdev.tildash.storage.LearningProgressStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class AppUserJourneyTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun learnerCanDownloadOpenAnswerAndRemovePublishedLesson() =
        runComposeUiTest {
            val downloadedStore = FakeDownloadedLessonStore()
            val progressStore = FakeLearningProgressStore()
            val transport = DemoContentTransport()
            var clockTick = 0L
            var attemptNumber = 0

            val clock =
                object : Clock {
                    override fun now(): Instant = Instant.fromEpochMilliseconds(1_000L + ++clockTick)
                }
            val lessonApplication =
                LearnerLessonApplication(
                    downloadedLessonStore = downloadedStore,
                    coordinator = LearnerLessonCoordinator(downloadedStore, progressStore),
                    clock = clock,
                    attemptIdGenerator = { "attempt-" + (++attemptNumber) },
                )
            val contentApplication =
                LearnerContentApplication(
                    transport = transport,
                    downloadedLessonStore = downloadedStore,
                    clock = clock,
                )

            setContent {
                App(
                    learnerApplication = lessonApplication,
                    contentApplication = contentApplication,
                )
            }

            onNodeWithText("Tildash Learning").assertIsDisplayed()
            onNodeWithText("Crimean Tatar basics").assertIsDisplayed()
            onNodeWithText("Greetings").assertIsDisplayed()

            onNodeWithText("Download").performClick()
            onNodeWithText("Open").assertIsDisplayed()
            onNodeWithText("Downloaded (1)").performClick()
            onNodeWithText("Open").performClick()

            onNodeWithText("Цель урока:", substring = true).assertIsDisplayed()
            onNodeWithText("Merhaba!").assertIsDisplayed()
            onNodeWithText("Привет! / Здравствуйте!").assertIsDisplayed()
            onNodeWithText("Вежливый вариант:", substring = true).assertIsDisplayed()
            onNodeWithText("Неформальный вариант:", substring = true).assertIsDisplayed()
            onNodeWithText("Вы хотите нейтрально поздороваться. Как сказать «Привет!» или «Здравствуйте!»?").assertIsDisplayed()
            onNodeWithTag("learner-answer-input").performTextInput("wrong")
            onNodeWithText("Check answer").performClick()
            onNodeWithText("Try again").assertIsDisplayed()
            onNodeWithText("Вы хотите нейтрально поздороваться. Как сказать «Привет!» или «Здравствуйте!»?").assertIsDisplayed()

            // Verify remove and re-download before completing the lesson; progress survives package removal.
            onNodeWithText("Back to lessons").performClick()
            onNodeWithText("Remove").performClick()
            onNodeWithText("No downloaded lessons yet. Open Catalog and download a published lesson.").assertIsDisplayed()

            onNodeWithText("Catalog").performClick()
            onNodeWithText("Download").performClick()
            onNodeWithText("Open").assertIsDisplayed()
            onNodeWithText("Downloaded (1)").performClick()
            onNodeWithText("Open").performClick()
            onNodeWithText("Вы хотите нейтрально поздороваться. Как сказать «Привет!» или «Здравствуйте!»?").assertIsDisplayed()

            onNodeWithTag("learner-answer-input").performTextInput("Merhaba")
            onNodeWithText("Check answer").performClick()
            onNodeWithText("1 / 6").assertIsDisplayed()
            onNodeWithText("Вы услышали «Selâm aleyküm!». Напишите правильный ответ на это приветствие.").assertIsDisplayed()

            onNodeWithTag("learner-answer-input").performTextInput("Aleyküm selâm")
            onNodeWithText("Check answer").performClick()
            onNodeWithText("2 / 6").assertIsDisplayed()
            onNodeWithText("Вы встретили друга. Напишите короткое неформальное приветствие.").assertIsDisplayed()

            onNodeWithTag("learner-answer-input").performTextInput("Selâm")
            onNodeWithText("Check answer").performClick()
            onNodeWithText("3 / 6").assertIsDisplayed()
            onNodeWithText("Какое приветствие обычно предшествует ответу «Aleyküm selâm!»?").assertIsDisplayed()

            onNodeWithTag("learner-answer-input").performTextInput("Selâm aleyküm")
            onNodeWithText("Check answer").performClick()
            onNodeWithText("4 / 6").assertIsDisplayed()
            onNodeWithText("Дополните диалог: A: Selâm aleyküm! B: ...").assertIsDisplayed()

            onNodeWithTag("learner-answer-input").performTextInput("Aleyküm selâm")
            onNodeWithText("Check answer").performClick()
            onNodeWithText("5 / 6").assertIsDisplayed()
            onNodeWithText("Вы знакомитесь со старшим человеком. Напишите традиционное уважительное приветствие.").assertIsDisplayed()

            onNodeWithTag("learner-answer-input").performTextInput("Selâm aleyküm")
            onNodeWithText("Check answer").performClick()
            onNodeWithText("Completed").assertIsDisplayed()
            onNodeWithText("Lesson complete.").assertIsDisplayed()

            onNodeWithText("Back to lessons").performClick()
            onNodeWithText("Remove").performClick()
            onNodeWithText("No downloaded lessons yet. Open Catalog and download a published lesson.").assertIsDisplayed()
            assertEquals(7, progressStore.savedAttempts.size)
            assertEquals(
                listOf(
                    AnswerOutcome.INCORRECT,
                    AnswerOutcome.CORRECT,
                    AnswerOutcome.CORRECT,
                    AnswerOutcome.CORRECT,
                    AnswerOutcome.CORRECT,
                    AnswerOutcome.CORRECT,
                    AnswerOutcome.CORRECT,
                ),
                progressStore.savedAttempts.map { it.outcome },
            )
            assertEquals(
                listOf("exercise-1", "exercise-1", "exercise-2", "exercise-3", "exercise-4", "exercise-5", "exercise-6"),
                progressStore.savedAttempts.map { it.exerciseId },
            )
            assertTrue(downloadedStore.listLessons().isEmpty())
        }

    private class DemoContentTransport : LearnerContentTransport {
        private val courseId = ContentId("550e8400-e29b-41d4-a716-446655440001")
        private val lessonId = ContentId("550e8400-e29b-41d4-a716-446655440000")
        private val lesson =
            LearnerLessonSummary(
                id = lessonId,
                title = "Greetings",
                sourceLocale = LanguageTag("crh"),
                publishedVersion = 1,
                localizations = emptyList(),
            )
        private val course =
            LearnerCourseSummary(
                id = courseId,
                title = "Crimean Tatar basics",
                sourceLocale = LanguageTag("crh"),
                publishedVersion = 1,
                lessons = listOf(lesson),
            )

        override suspend fun loadCatalog(): LearnerCourseCatalog = LearnerCourseCatalog(courses = listOf(course))

        override suspend fun loadLesson(lessonId: ContentId): LearnerLessonPackage {
            require(lessonId == this.lesson.id)
            return LearnerLessonPackage(
                course = course,
                lesson = lesson,
                plan =
                    LearningPlan(
                        lessonId = lesson.id,
                        publishedVersion = lesson.publishedVersion,
                        exercises =
                            listOf(
                                ManualInputExercise(
                                    id = "exercise-1",
                                    contentId = lesson.id,
                                    prompt = "Вы хотите нейтрально поздороваться. Как сказать «Привет!» или «Здравствуйте!»?",
                                    expectedAnswers = listOf("Merhaba", "Merhaba!"),
                                ),
                                ManualInputExercise(
                                    id = "exercise-2",
                                    contentId = lesson.id,
                                    prompt = "Вы услышали «Selâm aleyküm!». Напишите правильный ответ на это приветствие.",
                                    expectedAnswers = listOf("Aleyküm selâm", "Aleyküm selâm!"),
                                ),
                                ManualInputExercise(
                                    id = "exercise-3",
                                    contentId = lesson.id,
                                    prompt = "Вы встретили друга. Напишите короткое неформальное приветствие.",
                                    expectedAnswers = listOf("Selâm", "Selâm!", "Meraba", "Meraba!"),
                                ),
                                ManualInputExercise(
                                    id = "exercise-4",
                                    contentId = lesson.id,
                                    prompt = "Какое приветствие обычно предшествует ответу «Aleyküm selâm!»?",
                                    expectedAnswers = listOf("Selâm aleyküm", "Selâm aleyküm!"),
                                ),
                                ManualInputExercise(
                                    id = "exercise-5",
                                    contentId = lesson.id,
                                    prompt = "Дополните диалог: A: Selâm aleyküm! B: ...",
                                    expectedAnswers = listOf("Aleyküm selâm", "Aleyküm selâm!"),
                                ),
                                ManualInputExercise(
                                    id = "exercise-6",
                                    contentId = lesson.id,
                                    prompt = "Вы знакомитесь со старшим человеком. Напишите традиционное уважительное приветствие.",
                                    expectedAnswers = listOf("Selâm aleyküm", "Selâm aleyküm!"),
                                ),
                            ),
                    ),
                materials =
                    listOf(
                        LearnerLessonMaterial(
                            id = ContentId("550e8400-e29b-41d4-a716-446655440012"),
                            kind = ContentKind.EXPLANATION,
                            position = 0,
                            sourceLocale = LanguageTag("ru"),
                            payload = ContentPayload.Text("Цель урока: научиться приветствовать собеседника и отвечать на традиционное приветствие."),
                        ),
                        LearnerLessonMaterial(
                            id = ContentId("550e8400-e29b-41d4-a716-446655440013"),
                            kind = ContentKind.VOCABULARY,
                            position = 1,
                            sourceLocale = LanguageTag("crh"),
                            payload = ContentPayload.Vocabulary("Merhaba!", "Привет! / Здравствуйте!"),
                        ),
                        LearnerLessonMaterial(
                            id = ContentId("550e8400-e29b-41d4-a716-446655440014"),
                            kind = ContentKind.VOCABULARY,
                            position = 2,
                            sourceLocale = LanguageTag("crh"),
                            payload = ContentPayload.Vocabulary("Selâm aleyküm!", "Традиционное уважительное приветствие"),
                        ),
                        LearnerLessonMaterial(
                            id = ContentId("550e8400-e29b-41d4-a716-446655440015"),
                            kind = ContentKind.VOCABULARY,
                            position = 3,
                            sourceLocale = LanguageTag("crh"),
                            payload = ContentPayload.Vocabulary("Aleyküm selâm!", "Ответ на «Selâm aleyküm!»"),
                        ),
                        LearnerLessonMaterial(
                            id = ContentId("550e8400-e29b-41d4-a716-446655440016"),
                            kind = ContentKind.VOCABULARY,
                            position = 4,
                            sourceLocale = LanguageTag("crh"),
                            payload = ContentPayload.Vocabulary("Selâm!", "Привет! (неформально)"),
                        ),
                        LearnerLessonMaterial(
                            id = ContentId("550e8400-e29b-41d4-a716-446655440017"),
                            kind = ContentKind.EXPLANATION,
                            position = 5,
                            sourceLocale = LanguageTag("ru"),
                            payload = ContentPayload.Text("Выбирайте приветствие по ситуации: уважительная форма для незнакомых и старших, короткая — для друзей."),
                        ),
                        LearnerLessonMaterial(
                            id = ContentId("550e8400-e29b-41d4-a716-446655440018"),
                            kind = ContentKind.EXAMPLE,
                            position = 6,
                            sourceLocale = LanguageTag("crh"),
                            payload = ContentPayload.Text("Вежливый вариант: A: Selâm aleyküm! — Здравствуйте! / B: Aleyküm selâm! — Здравствуйте!"),
                        ),
                        LearnerLessonMaterial(
                            id = ContentId("550e8400-e29b-41d4-a716-446655440019"),
                            kind = ContentKind.EXAMPLE,
                            position = 7,
                            sourceLocale = LanguageTag("crh"),
                            payload = ContentPayload.Text("Неформальный вариант: A: Selâm! — Привет! / B: Selâm! — Привет!"),
                        ),
                    ),
            )
        }
    }

    private class FakeDownloadedLessonStore : DownloadedLessonStore {
        private val lessons = linkedMapOf<ContentId, DownloadedLesson>()

        override fun save(downloadedLesson: DownloadedLesson) {
            lessons[downloadedLesson.lesson.id] = downloadedLesson
        }

        override fun loadLesson(lessonId: ContentId): DownloadedLesson? = lessons[lessonId]

        override fun listLessons(): List<DownloadedLesson> = lessons.values.toList()

        override fun deleteLesson(lessonId: ContentId) {
            lessons.remove(lessonId)
        }

        override fun countLessons(): Long = lessons.size.toLong()
    }

    private class FakeLearningProgressStore : LearningProgressStore {
        val savedAttempts = mutableListOf<LearningAttempt>()

        override fun loadProgress(): LearningProgress = LearningProgress.fromPersistedAttempts(savedAttempts.toList())

        override fun saveAttempt(attempt: LearningAttempt) {
            savedAttempts += attempt
        }

        override fun loadPendingSyncAttempts(): List<LearningAttempt> = emptyList()

        override fun acknowledgeAttempt(attemptId: String) = Unit

        override fun pendingSyncCount(): Long = 0L
    }
}
