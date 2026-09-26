package com.kenlikdev.tildash

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() =
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Tildash",
        ) {
            App()
        }
    }
