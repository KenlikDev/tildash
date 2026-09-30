package com.kenlikdev.tildash.server.learning

import com.kenlikdev.tildash.learning.LearnerCourseCatalog
import com.kenlikdev.tildash.learning.LearningCatalogProjector
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LearningCatalogService(
    private val repository: PublishedLearningCatalogRepository,
) {
    @Transactional(readOnly = true)
    fun catalog(): LearnerCourseCatalog {
        val source = repository.load()
        return LearningCatalogProjector.project(
            nodes = source.nodes,
            publishedVersions = source.publishedVersions,
        )
    }
}
