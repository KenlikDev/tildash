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

data class LearningLessonPackageResponse(
    val course: LearningCourseResponse,
    val lesson: LearningLessonResponse,
    val exercises: List<LearningExerciseResponse>,
)

data class LearningExerciseResponse(
    val id: String,
    val contentId: String,
    val type: String,
    val prompt: String,
    val expectedAnswers: List<String>,
)
