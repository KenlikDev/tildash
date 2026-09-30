package com.kenlikdev.tildash.server.api.learning

import com.kenlikdev.tildash.server.learning.LearningCatalogService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/learning")
class LearningCatalogController(
    private val service: LearningCatalogService,
) {
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

private fun com.kenlikdev.tildash.learning.LearnerCourseCatalog.toResponse(): LearningCatalogResponse =
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
