package com.kenlikdev.tildash.learning

import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.content.model.ContentKind
import com.kenlikdev.tildash.content.model.ContentPayload
import com.kenlikdev.tildash.content.model.LanguageTag

data class LearnerLessonMaterial(
    val id: ContentId,
    val kind: ContentKind,
    val position: Int,
    val sourceLocale: LanguageTag,
    val payload: ContentPayload,
) {
    init {
        require(position >= 0) {
            "Lesson material position must not be negative."
        }
        when (kind) {
            ContentKind.EXPLANATION,
            ContentKind.EXAMPLE,
            -> require(payload is ContentPayload.Text) {
                "Explanation and example materials must contain text."
            }

            ContentKind.VOCABULARY -> require(payload is ContentPayload.Vocabulary) {
                "Vocabulary materials must contain a term and a gloss."
            }

            else -> throw IllegalArgumentException("Unsupported learner lesson material kind '$kind'.")
        }
    }
}

data class LearnerLessonPackage(
    val course: LearnerCourseSummary,
    val lesson: LearnerLessonSummary,
    val plan: LearningPlan,
    val materials: List<LearnerLessonMaterial> = emptyList(),
) {
    init {
        require(plan.lessonId == lesson.id) {
            "Learning plan lesson ID must match the learner lesson."
        }
        require(course.lessons.any { it.id == lesson.id }) {
            "Learner lesson must belong to its course."
        }
        require(materials.map { it.id }.distinct().size == materials.size) {
            "Lesson material IDs must be unique."
        }
        require(materials.map { it.position }.distinct().size == materials.size) {
            "Lesson material positions must be unique."
        }
    }
}
