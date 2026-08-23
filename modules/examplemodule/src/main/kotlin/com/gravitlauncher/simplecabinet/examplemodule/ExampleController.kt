package com.gravitlauncher.simplecabinet.examplemodule

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class ExampleController {
    @GetMapping("/example-module")
    fun hello(): Map<String, String> = mapOf("module" to "examplemodule", "status" to "loaded")
}
