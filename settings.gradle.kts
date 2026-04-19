pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
	}
}

plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
	repositories {
		mavenCentral()
	}
	
	versionCatalogs {
		// Configure the existing libs catalog
		configureEach {
			if (name == "libs") {
				// Allow overriding Kotlin version for testing
				val kotlinVersionOverride = providers.gradleProperty("test.kotlin.version")
					.orElse(providers.environmentVariable("TEST_KOTLIN_VERSION"))
				
				if (kotlinVersionOverride.isPresent) {
					val versionValue = kotlinVersionOverride.get()
					println("Overriding Kotlin version to $versionValue for testing")
					
					// Override the kotlin version
					version("kotlin", versionValue)
					
					// Also need to update the plugin versions to match
					plugin("kotlin-multiplatform", "org.jetbrains.kotlin.multiplatform").version(versionValue)
					plugin("kotlin-jvm", "org.jetbrains.kotlin.jvm").version(versionValue)
					plugin("kotlin-serialization", "org.jetbrains.kotlin.plugin.serialization").version(versionValue)
				}
			}
		}
	}
}

rootProject.name = "mcp4k"
include("mcp4k-compiler")
include("mcp4k-file-provider")
include("mcp4k-gradle")
include("mcp4k-ksp")
include("mcp4k-runtime")
include("mcp4k-test")

includeBuild("mcp4k-build") {
	dependencySubstitution {
		substitute(module("sh.ondr.mcp4k:mcp4k-gradle")).using(project(":gradle-plugin"))
	}
}
