package com.kenlikdev.tildash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kenlikdev.tildash.learning.client.createLearnerLessonApplication

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val learnerApplication =
            createLearnerLessonApplication(
                createTildashDatabaseDriver(applicationContext),
            )

        setContent {
            App(learnerApplication)
        }
    }
}

@Preview
@Composable
fun appAndroidPreview() {
    App()
}
