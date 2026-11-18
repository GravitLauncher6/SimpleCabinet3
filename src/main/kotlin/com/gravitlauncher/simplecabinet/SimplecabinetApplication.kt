package com.gravitlauncher.simplecabinet

import com.gravitlauncher.simplecabinet.cli.AdminCommandRunner
import com.gravitlauncher.simplecabinet.module.ModuleBootstrapInitializer
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SimplecabinetApplication

fun main(args: Array<String>) {
    if (args.none { it in AdminCommandRunner.commands }) {
        startApplication(args)
        return
    }

    val result = startApplication(arrayOf(*args, "--server.port=0")).use { context ->
        context.getBean(AdminCommandRunner::class.java).execute(args)
    }
    if (result != null) println(result)
}

private fun startApplication(args: Array<String>) =
    runApplication<SimplecabinetApplication>(*args) {
        addInitializers(ModuleBootstrapInitializer())
    }
