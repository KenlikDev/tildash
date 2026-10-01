package com.kenlikdev.tildash.server.api.learning

import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.learning.LearnerCourseCatalog
import com.kenlikdev.tildash.learning.LearnerLessonPackage
import com.kenlikdev.tildash.learning.ManualInputExercise
import com.kenlikdev.tildash.server.learning.LearningCatalogService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/learning")
class LearningCatalogController(
    private val service: LearningCatalogService,
) {
    @GetMapping("/lessons/{lessonId}")
    @PreAuthorize("hasRole('LEARNER')")
    @Operation(summary = "Read a published learner lesson package")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Published lesson package returned"),
        ApiResponse(responseCode = "401", description = "Authentication required"),
        ApiResponse(responseCode = "403", description = "Learner role required"),
        ApiResponse(responseCode = "404", description = "Published lesson not found"),
    )
    fun lesson(
        @PathVariable lessonId: String,
    ): LearningLessonPackageResponse =
        service.lesson(ContentId(lessonId)).toResponse()

    @GetMapping("/catalog")
    @PreAuthorize("hasRole('LEARNER')")
    @Operation(summary = "Read the published learner course catalog")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Published learner catalog returned"),
        ApiResponse(responseCode = "401", description = "Authentication required"),
        ApiResponse(responseCode = "403", description = "Learner role required"),
    )
    fun catalog(): LearningCatalogResponse =
        service
            .catalog()
            .toResponse()
}

private fun LearnerCourseCatalog.toResponse(): LearningCatalogResponse =
    LearningCatalogResponse(
        courses =
            courses.map { course ->
                LearningCourseResponse(
                    id = course.id.value,
                    title = course.title,
                    sourceLocale = course.sourceLocale.value,
                    publishedVersion = course.publishedVersion,
                    lessons =
                        course.lessons.map { lesson ->
                            LearningLessonResponse(
                                id = lesson.id.value,
                                title = lesson.title,
                                sourceLocale = lesson.sourceLocale.value,
                                publishedVersion = lesson.publishedVersion,
                                localizations =
                                    lesson.localizations.map { localization ->
                                        LearningLocalizedTextResponse(
                                            locale = localization.locale.value,
                                            value = localization.value,
                                        )
                                    },
                            )
                        },
                )
            },
    )

private fun LearnerLessonPackage.toResponse(): LearningLessonPackageResponse =
    LearningLessonPackageResponse(
        course =
            LearningCourseResponse(
                id = course.id.value,
                title = course.title,
                sourceLocale = course.sourceLocale.value,
                publishedVersion = course.publishedVersion,
                lessons = course.lessons.map { lesson ->
                    LearningLessonResponse(
                        id = lesson.id.value,
                        title = lesson.title,
                        sourceLocale = lesson.sourceLocale.value,
                        publishedVersion = lesson.publishedVersion,
                        localizations = lesson.localizations.map { localization ->
                            LearningLocalizedTextResponse(
                                locale = localization.locale.value,
                                value = localization.value,
                            )
                        },
                    )
                },
            ),
        lesson =
            LearningLessonResponse(
                id = lesson.id.value,
                title = lesson.title,
                sourceLocale = lesson.sourceLocale.value,
                publishedVersion = lesson.publishedVersion,
                localizations =
                    lesson.localizations.map { localization ->
                        LearningLocalizedTextResponse(
                            locale = localization.locale.value,
                            value = localization.value,
                        )
                    },
            ),
        exercises =
            plan.exercises.map { exercise ->
                when (exercise) {
                    is ManualInputExercise -> {
                        LearningExerciseResponse(
                            id = exercise.id,
                            contentId = exercise.contentId.value,
                            type = "MANUAL_INPUT",
                            prompt = exercise.prompt,
                            expectedAnswers = exercise.expectedAnswers,
                        )
                    }

                    else -> {
                        error(
                            "Unsupported learner exercise type: " +
                                exercise::class.simpleName,
                        )
                    }
                }
            },
    )
