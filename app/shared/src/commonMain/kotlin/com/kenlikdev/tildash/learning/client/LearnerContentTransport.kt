package com.kenlikdev.tildash.learning.client

import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.learning.LearnerCourseCatalog
import com.kenlikdev.tildash.learning.LearnerLessonPackage

interface LearnerContentTransport {
    suspend fun loadCatalog(): LearnerCourseCatalog

    suspend fun loadLesson(lessonId: ContentId): LearnerLessonPackage
}
