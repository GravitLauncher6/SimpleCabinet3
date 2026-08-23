package com.gravitlauncher.simplecabinet.module

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.Ordered
import org.springframework.core.PriorityOrdered
import org.springframework.context.annotation.Configuration
import java.io.File
import java.net.URLClassLoader
import java.util.jar.JarFile

/**
 * Loads module configuration before Spring Boot parses the application's configuration.
 * A module is a jar (or exploded directory) placed in [path].
 */
class ModuleBootstrapInitializer(
    private val path: File = File("modules")
) : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        val loader = ModuleClassLoaderFactory.create(path, applicationContext.classLoader ?: javaClass.classLoader)
        if (loader.modules.isEmpty()) return

        applicationContext.beanFactory.beanClassLoader = loader.classLoader
        applicationContext.addBeanFactoryPostProcessor(
            ModuleConfigurationRegistrar(loader.configurationClasses)
        )
    }
}

private class ModuleConfigurationRegistrar(
    private val configurationClasses: List<Class<*>>
) : BeanDefinitionRegistryPostProcessor, PriorityOrdered {
    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    override fun postProcessBeanDefinitionRegistry(registry: BeanDefinitionRegistry) {
        configurationClasses.forEach { type ->
            val definition = RootBeanDefinition(type)
            definition.role = BeanDefinition.ROLE_APPLICATION
            registry.registerBeanDefinition(type.name, definition)
        }
    }

    override fun postProcessBeanFactory(beanFactory: org.springframework.beans.factory.config.ConfigurableListableBeanFactory) = Unit
}

private data class ModuleClassLoaderResult(
    val classLoader: ClassLoader,
    val configurationClasses: List<Class<*>>,
    val modules: List<File>
)

private object ModuleClassLoaderFactory {
    private val log = LoggerFactory.getLogger(ModuleClassLoaderFactory::class.java)

    fun create(directory: File, parent: ClassLoader): ModuleClassLoaderResult {
        if (!directory.exists()) {
            log.debug("Modules directory does not exist: {}", directory.absolutePath)
            return ModuleClassLoaderResult(parent, emptyList(), emptyList())
        }
        val modules: List<File> = directory.listFiles()
            ?.filter { file -> file.isDirectory || file.extension.equals("jar", true) }
            ?.sortedBy { file -> file.name }
            ?: emptyList()
        if (modules.isEmpty()) return ModuleClassLoaderResult(parent, emptyList(), emptyList())

        val loader = URLClassLoader(modules.map { it.toURI().toURL() }.toTypedArray(), parent)
        val configurations = modules.flatMap { module -> classesIn(module).toList() }.mapNotNull { name ->
            runCatching { Class.forName(name, false, loader) }
                .onFailure { log.warn("Cannot inspect module class {}", name, it) }
                .getOrNull()
        }.filter { it.isAnnotationPresent(Configuration::class.java) }
            .distinctBy { it.name }

        log.info("Loaded {} module(s), found {} configuration class(es)", modules.size, configurations.size)
        return ModuleClassLoaderResult(loader, configurations, modules)
    }

    private fun classesIn(module: File): Sequence<String> = sequence {
        if (module.isDirectory) {
            module.walkTopDown().filter { it.isFile && it.extension == "class" }.forEach {
                yield(it.relativeTo(module).path.removeSuffix(".class").replace(File.separatorChar, '.'))
            }
        } else {
            JarFile(module).use { jar ->
                jar.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".class") }
                    .forEach { yield(it.name.removeSuffix(".class").replace('/', '.')) }
            }
        }
    }
}
