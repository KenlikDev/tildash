package com.kenlikdev.tildash.learning.client

import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.storage.DownloadedLesson
import com.kenlikdev.tildash.storage.DownloadedLessonStore
import kotlin.time.Clock

class LearnerContentApplication(
    private val transport: LearnerContentTransport,
    private val downloadedLessonStore: DownloadedLessonStore,
    private val clock: Clock = Clock.System,
) {
    suspend fun loadCatalog() = transport.loadCatalog()

    suspend fun downloadLesson(lessonId: ContentId): DownloadedLesson {
        val packageData = transport.loadLesson(lessonId)
        val downloadedLesson =
            DownloadedLesson(
                course = packageData.course,
                lesson = packageData.lesson,
                plan = packageData.plan,
                downloadedAt = clock.now(),
            )
        downloadedLessonStore.save(downloadedLesson)
        return downloadedLesson
    }

    fun listDownloadedLessons(): List<DownloadedLesson> = downloadedLessonStore.listLessons()

    fun isDownloaded(lessonId: ContentId): Boolean =
        downloadedLessonStore
            .loadLesson(lessonId)
            != null

    fun deleteDownloadedLesson(lessonId: ContentId) {
        downloadedLessonStore.deleteLesson(lessonId)
    }
}
