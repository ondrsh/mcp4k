import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.ondrsh.mcp4k) // Will not use GAV coordinates, will be substituted
	alias(libs.plugins.maven.publish)
}

kotlin {
	jvm()

	js(IR) {
		nodejs()
		binaries.library()
	}

	when {
		// macOS can build & publish all
		HostManager.hostIsMac -> {
			iosArm64()
			iosSimulatorArm64()
			iosX64()
			macosArm64()
			macosX64()
			linuxX64()
			mingwX64()
		}

		HostManager.hostIsLinux -> {
			linuxX64()
		}

		HostManager.hostIsMingw -> {
			mingwX64()
		}
	}

	sourceSets {
		commonMain {
			dependencies {
				implementation(libs.kotlinx.serialization.json)
				implementation(libs.kotlinx.coroutines.core)
			}
		}
		commonTest {
			dependencies {
				implementation(kotlin("test"))
				implementation(libs.kotlinx.coroutines.test)
				implementation(libs.square.okio)
				implementation(libs.square.okio.fakefilesystem)
				implementation(project(":mcp4k-file-provider"))
			}
		}
	}
}

tasks.withType<Test>().configureEach {
	useJUnitPlatform()
	
	testLogging {
		events("passed", "skipped", "failed")
		showExceptions = true
		showCauses = true
		showStackTraces = true
	}
}

// Disable publishing for this test module
tasks.withType<PublishToMavenRepository>().configureEach {
	enabled = false
}
tasks.withType<PublishToMavenLocal>().configureEach {
	enabled = false
}

// Task to verify the plugin was applied correctly
tasks.register("verifyPluginApplication") {
	doLast {
		println("Kotlin version: ${libs.versions.kotlin.get()}")
		println("KSP tasks: ${tasks.names.filter { it.contains("ksp", ignoreCase = true) }}")
		println("MCP4K plugin applied: ${project.plugins.hasPlugin("sh.ondr.mcp4k")}")
	}
}

// Task to print the effective Kotlin version
tasks.register("printKotlinVersion") {
	doLast {
		println("Using Kotlin version: ${libs.versions.kotlin.get()}")
	}
}

// Task to check actual Kotlin compiler version being used
tasks.register("checkActualKotlinVersion") {
	doLast {
		val kotlinPlugin = project.plugins.findPlugin("org.jetbrains.kotlin.multiplatform")
		println("Kotlin Multiplatform Plugin Applied: ${kotlinPlugin != null}")
		if (kotlinPlugin != null) {
			println("Plugin Class: ${kotlinPlugin::class.java.name}")
			// Try to access the version through the plugin
			try {
				val versionField = kotlinPlugin::class.java.declaredFields.find { it.name.contains("version", true) }
				if (versionField != null) {
					versionField.isAccessible = true
					println("Version field: ${versionField.get(kotlinPlugin)}")
				}
			} catch (e: Exception) {
				println("Could not access version field: ${e.message}")
			}
		}
		
		// Check compiler classpath
		configurations.findByName("kotlinCompilerClasspath")?.let { config ->
			println("\nKotlin Compiler Classpath:")
			config.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
				if (artifact.moduleVersion.id.group == "org.jetbrains.kotlin") {
					println("  ${artifact.moduleVersion.id}: ${artifact.file.name}")
				}
			}
		}
	}
}
