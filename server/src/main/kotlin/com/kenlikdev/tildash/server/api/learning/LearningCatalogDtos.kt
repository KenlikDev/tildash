package com.kenlikdev.tildash.server.api.learning

data class LearningCatalogResponse(
    val courses: List<LearningCourseResponse>,
)

data class LearningCourseResponse(
    val id: String,
    val title: String,
    val sourceLocale: String,
    val publishedVersion: Int,
    val lessons: List<LearningLessonResponse>,
)

data class LearningLessonResponse(
    val id: String,
    val title: String,
    val sourceLocale: String,
    val publishedVersion: Int,
    val localizations: List<LearningLocalizedTextResponse>,
)

data class LearningLocalizedTextResponse(
    val locale: String,
    val value: String,
)
