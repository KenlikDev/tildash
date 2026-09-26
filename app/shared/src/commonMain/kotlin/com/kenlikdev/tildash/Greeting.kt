package com.kenlikdev.tildash

class Greeting {
    private val platform = getPlatform()

    fun greet(): String = sayHello(platform.name)
}
