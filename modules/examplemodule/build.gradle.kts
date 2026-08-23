plugins {
	kotlin("jvm") version "2.2.21"
	kotlin("plugin.spring") version "2.2.21"
}

group = "com.gravitlauncher.simplecabinet.modules"
version = "0.1.0"

repositories { mavenCentral() }

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

dependencies {
	// Supplied by the main application. These are needed to compile the
	// module, but are intentionally absent from the packaged module jar.
	compileOnly("org.springframework:spring-context:7.0.0")
	compileOnly("org.springframework:spring-web:7.0.0")
	compileOnly("org.springframework.boot:spring-boot:4.0.6")
	compileOnly("org.springframework.security:spring-security-config:7.0.0")
	compileOnly("org.springframework.security:spring-security-web:7.0.0")
}

tasks.jar {
	archiveBaseName = "examplemodule"
	// A module jar contains its own output only. Self-owned implementation
	// dependencies should be added to `implementation` and are copied below.
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val moduleJar = tasks.register<Jar>("moduleJar") {
	group = "modules"
	description = "Builds the deployable example module jar"
	archiveBaseName = project.name
	destinationDirectory = rootProject.layout.projectDirectory.dir("modules")
	from(sourceSets.main.get().output)
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
	// Keep compileOnly dependencies (including the application API) out.
	from(configurations.runtimeClasspath.get()
		.filterNot { file ->
			file.name.startsWith("kotlin-") || file.name.startsWith("annotations-")
		}
		.map { if (it.isDirectory) it else zipTree(it) })
	with(tasks.jar.get())
}

tasks.assemble { dependsOn(moduleJar) }
