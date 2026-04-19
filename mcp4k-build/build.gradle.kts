import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin

plugins {
	alias(libs.plugins.build.config)
	alias(libs.plugins.kotlin.jvm).apply(false)
}

allprojects {
	version = "mcp4k-internal"

	repositories {
		mavenCentral()
		gradlePluginPortal()
	}

	plugins.withType<KotlinBasePlugin> {
		extensions.configure<KotlinProjectExtension> {
			jvmToolchain(11)
		}
	}
}
