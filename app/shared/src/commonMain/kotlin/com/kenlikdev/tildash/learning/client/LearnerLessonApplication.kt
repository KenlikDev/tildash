package com.kenlikdev.tildash.learning.client

import app.cash.sqldelight.db.SqlDriver
import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.storage.DownloadedLesson
import com.kenlikdev.tildash.storage.DownloadedLessonStore
import com.kenlikdev.tildash.storage.LearningProgressStore
import com.kenlikdev.tildash.storage.SqlDelightDownloadedLessonStore
import com.kenlikdev.tildash.storage.SqlDelightLearningProgressStore
import kotlin.time.Clock
import kotlin.uuid.Uuid

class LearnerLessonApplication(
    private val downloadedLessonStore: DownloadedLessonStore,
    private val coordinator: LearnerLessonCoordinator,
    private val clock: Clock = Clock.System,
    private val attemptIdGenerator: () -> String = { Uuid.random().toString() },
) {
    fun listDownloadedLessons(): List<DownloadedLesson> =
        downloadedLessonStore.listLessons()

    fun openLesson(lessonId: ContentId): LearnerLessonState? = coordinator.open(lessonId)

    fun submitText(
        state: LearnerLessonState,
        value: String,
    ): LearnerLessonState =
        coordinator.submitText(
            state = state,
            attemptId = attemptIdGenerator(),
            value = value,
            occurredAt = clock.now(),
        )
}

fun createLearnerLessonApplication(
    driver: SqlDriver,
    clock: Clock = Clock.System,
    attemptIdGenerator: () -> String = { Uuid.random().toString() },
): LearnerLessonApplication {
    val downloadedLessonStore = SqlDelightDownloadedLessonStore(driver)
    val learningProgressStore = SqlDelightLearningProgressStore(driver)

    return LearnerLessonApplication(
        downloadedLessonStore = downloadedLessonStore,
        coordinator =
            LearnerLessonCoordinator(
                downloadedLessonStore = downloadedLessonStore,
                learningProgressStore = learningProgressStore,
            ),
        clock = clock,
        attemptIdGenerator = attemptIdGenerator,
    )
}
