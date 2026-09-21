package com.kenlikdev.tildash.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TildashServerApplication

fun main(args: Array<String>) {
    runApplication<TildashServerApplication>(*args)
}
