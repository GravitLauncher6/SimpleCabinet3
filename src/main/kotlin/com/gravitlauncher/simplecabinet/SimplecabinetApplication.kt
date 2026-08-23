package com.gravitlauncher.simplecabinet

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import com.gravitlauncher.simplecabinet.module.ModuleBootstrapInitializer

@SpringBootApplication
class SimplecabinetApplication

fun main(args: Array<String>) {
	runApplication<SimplecabinetApplication>(*args) {
		addInitializers(ModuleBootstrapInitializer())
	}
}
