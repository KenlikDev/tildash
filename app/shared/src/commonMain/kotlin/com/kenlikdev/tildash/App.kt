package com.kenlikdev.tildash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.learning.LearnerCourseCatalog
import com.kenlikdev.tildash.learning.client.LearnerContentApplication
import com.kenlikdev.tildash.learning.client.LearnerLessonApplication
import com.kenlikdev.tildash.learning.client.LearnerLessonScreen
import com.kenlikdev.tildash.learning.client.LearnerLessonState
import com.kenlikdev.tildash.storage.DownloadedLesson
import kotlinx.coroutines.launch

private enum class LearnerHomeTab {
    CATALOG,
    DOWNLOADED,
}

@Composable
fun App(
    learnerApplication: LearnerLessonApplication? = null,
    contentApplication: LearnerContentApplication? = null,
) {
    MaterialTheme {
        when {
            learnerApplication != null && contentApplication != null -> {
                LearnerApplicationContent(learnerApplication, contentApplication)
            }

            learnerApplication != null -> {
                LocalDownloadedLessonsContent(learnerApplication)
            }

            else -> {
                PreviewOnlyState()
            }
        }
    }
}

@Composable
private fun LearnerApplicationContent(
    learnerApplication: LearnerLessonApplication,
    contentApplication: LearnerContentApplication,
) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(LearnerHomeTab.CATALOG) }
    var catalog by remember { mutableStateOf<LearnerCourseCatalog?>(null) }
    var downloaded by remember { mutableStateOf(contentApplication.listDownloadedLessons()) }
    var lessonState by remember { mutableStateOf<LearnerLessonState?>(null) }
    var loadingCatalog by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf<ContentId?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refreshDownloaded() {
        downloaded = contentApplication.listDownloadedLessons()
    }

    fun refreshCatalog() {
        scope.launch {
            loadingCatalog = true
            error = null
            try {
                catalog = contentApplication.loadCatalog()
            } catch (failure: Exception) {
                error = failure.message ?: "Unable to load the learner catalog."
            } finally {
                loadingCatalog = false
            }
        }
    }

    LaunchedEffect(contentApplication) {
        refreshCatalog()
    }

    lessonState?.let { state ->
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { lessonState = null }) {
                Text("Back to lessons")
            }
            LearnerLessonScreen(
                state = state,
                onSubmitAnswer = { value ->
                    lessonState = learnerApplication.submitText(state, value)
                },
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Tildash Learning", style = MaterialTheme.typography.headlineMedium)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tabButton("Catalog", tab == LearnerHomeTab.CATALOG) { tab = LearnerHomeTab.CATALOG }
            tabButton("Downloaded (" + downloaded.size + ")", tab == LearnerHomeTab.DOWNLOADED) {
                tab = LearnerHomeTab.DOWNLOADED
            }
            OutlinedButton(onClick = { refreshCatalog() }, enabled = !loadingCatalog) {
                Text("Refresh")
            }
        }

        error?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }

        when (tab) {
            LearnerHomeTab.CATALOG -> {
                CatalogContent(
                    catalog = catalog,
                    loading = loadingCatalog,
                    downloaded = downloaded,
                    downloadingLessonId = downloading,
                    onDownload = { id ->
                        scope.launch {
                            downloading = id
                            error = null
                            try {
                                contentApplication.downloadLesson(id)
                                refreshDownloaded()
                            } catch (failure: Exception) {
                                error = failure.message ?: "Unable to download the lesson."
                            } finally {
                                downloading = null
                            }
                        }
                    },
                    onOpen = { id ->
                        lessonState = learnerApplication.openLesson(id)
                        tab = LearnerHomeTab.DOWNLOADED
                    },
                )
            }

            LearnerHomeTab.DOWNLOADED -> {
                DownloadedLessonsContent(
                    downloadedLessons = downloaded,
                    onOpen = { id -> lessonState = learnerApplication.openLesson(id) },
                    onRemove = { id ->
                        contentApplication.deleteDownloadedLesson(id)
                        refreshDownloaded()
                    },
                )
            }
        }
    }
}

@Composable
private fun tabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick) {
            Text(label)
        }
    }
}

@Composable
private fun CatalogContent(
    catalog: LearnerCourseCatalog?,
    loading: Boolean,
    downloaded: List<DownloadedLesson>,
    downloadingLessonId: ContentId?,
    onDownload: (ContentId) -> Unit,
    onOpen: (ContentId) -> Unit,
) {
    when {
        loading && catalog == null -> {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text("Loading published lessons…")
            }
        }

        catalog == null -> {
            Text("The learner catalog has not been loaded.")
        }

        catalog.courses.isEmpty() -> {
            Text("No published courses are available for this learner.")
        }

        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                catalog.courses.forEach { course ->
                    item(key = "course-" + course.id.value) {
                        Text(course.title, style = MaterialTheme.typography.titleLarge)
                    }
                    items(course.lessons, key = { it.id.value }) { lesson ->
                        val isDownloaded = downloaded.any { it.lesson.id == lesson.id }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(lesson.title, style = MaterialTheme.typography.titleMedium)
                                Text("Published version " + lesson.publishedVersion)
                            }

                            when {
                                downloadingLessonId == lesson.id -> {
                                    CircularProgressIndicator()
                                }

                                isDownloaded -> {
                                    Button(onClick = { onOpen(lesson.id) }) {
                                        Text("Open")
                                    }
                                }

                                else -> {
                                    Button(onClick = { onDownload(lesson.id) }) {
                                        Text("Download")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
}

@Composable
private fun DownloadedLessonsContent(
    downloadedLessons: List<DownloadedLesson>,
    onOpen: (ContentId) -> Unit,
    onRemove: (ContentId) -> Unit,
) {
    if (downloadedLessons.isEmpty()) {
        Text("No downloaded lessons yet. Open Catalog and download a published lesson.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(downloadedLessons, key = { it.lesson.id.value }) { lesson ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(lesson.lesson.title, style = MaterialTheme.typography.titleMedium)
                    Text(lesson.course.title)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onOpen(lesson.lesson.id) }) { Text("Open") }
                    OutlinedButton(onClick = { onRemove(lesson.lesson.id) }) { Text("Remove") }
                }
            }
        }
    }
}

@Composable
private fun LocalDownloadedLessonsContent(learnerApplication: LearnerLessonApplication) {
    var lessonState by remember { mutableStateOf<LearnerLessonState?>(null) }
    val downloadedLessons =
        remember(learnerApplication) {
            learnerApplication.listDownloadedLessons()
        }

    lessonState?.let { state ->
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            OutlinedButton(onClick = { lessonState = null }) { Text("Back") }
            LearnerLessonScreen(
                state = state,
                onSubmitAnswer = { value ->
                    lessonState = learnerApplication.submitText(state, value)
                },
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    if (downloadedLessons.isEmpty()) {
        Text("No downloaded lessons are available.", modifier = Modifier.padding(24.dp))
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            items(downloadedLessons, key = { it.lesson.id.value }) { lesson ->
                Button(
                    onClick = { lessonState = learnerApplication.openLesson(lesson.lesson.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(lesson.lesson.title)
                }
            }
        }
    }
}

@Composable
private fun PreviewOnlyState() {
    Text("Application composition is not available in this preview.", modifier = Modifier.padding(24.dp))
}
