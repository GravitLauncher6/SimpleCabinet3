rootProject.name = "simplecabinet"

// Each directory containing a build.gradle(.kts) is a separately packaged
// runtime module. The application itself is deliberately not a dependency of
// these projects, so its libraries are not copied into module jars.
file("modules").listFiles()
	.orEmpty()
	.filter { it.isDirectory && (File(it, "build.gradle.kts").isFile || File(it, "build.gradle").isFile) }
	.sortedBy { it.name }
	.forEach { include(":modules:${it.name}") }
