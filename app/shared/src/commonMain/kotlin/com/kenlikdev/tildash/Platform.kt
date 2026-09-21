package com.kenlikdev.tildash

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform