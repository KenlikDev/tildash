package com.kenlikdev.tildash.learning

data class LearnerLessonPackage(
    val course: LearnerCourseSummary,
    val lesson: LearnerLessonSummary,
    val plan: LearningPlan,
) {
    init {
        require(plan.lessonId == lesson.id) {
            "Learning plan lesson ID must match the learner lesson."
        }
        require(course.lessons.any { it.id == lesson.id }) {
            "Learner lesson must belong to its course."
        }
    }
}
