package com.kenlikdev.tildash.server.learning

import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.learning.LearnerCourseCatalog
import com.kenlikdev.tildash.learning.LearnerLessonPackage
import com.kenlikdev.tildash.learning.LearningCatalogProjector
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LearningCatalogService(
    private val repository: PublishedLearningCatalogRepository,
    private val lessonRepository: PublishedLearningLessonRepository,
) {
    @Transactional(readOnly = true)
    fun lesson(lessonId: ContentId): LearnerLessonPackage = lessonRepository.load(lessonId)

    @Transactional(readOnly = true)
    fun catalog(): LearnerCourseCatalog {
        val source = repository.load()
        return LearningCatalogProjector.project(
            nodes = source.nodes,
            publishedVersions = source.publishedVersions,
        )
    }
}
