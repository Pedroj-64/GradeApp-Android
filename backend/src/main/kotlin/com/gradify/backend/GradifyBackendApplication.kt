package com.gradify.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class GradifyBackendApplication

fun main(args: Array<String>) {
    runApplication<GradifyBackendApplication>(*args)
}
