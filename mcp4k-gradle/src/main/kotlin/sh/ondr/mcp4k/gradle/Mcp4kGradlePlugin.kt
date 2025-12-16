package sh.ondr.mcp4k.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import sh.ondr.koja.gradle.KojaGradlePlugin
import kotlin.jvm.java

class Mcp4kGradlePlugin : KotlinCompilerPluginSupportPlugin {
	override fun apply(target: Project) {
		// Check Kotlin version
		target.checkKotlinVersion()

		// Check KSP version and apply
		target.checkKspVersion()

		// Get mcp4k dependencies
		val kspDependency = target.getKspDependency()
		val runtimeDependency = target.getRuntimeDependency()

		// Immediately fail if Koja is already applied
		if (target.plugins.hasPlugin("sh.ondr.koja")) {
			error(
				"The Koja plugin should not be applied separately when using mcp4k. " +
					"Remove the 'sh.ondr.koja' plugin from your build - mcp4k will handle it automatically.",
			)
		}

		// Or fail if Koja is applied later
		target.pluginManager.withPlugin("sh.ondr.koja") {
			error(
				"The Koja plugin should not be applied separately when using mcp4k. " +
					"Remove the 'sh.ondr.koja' plugin from your build - mcp4k will handle it automatically.",
			)
		}

		// Apply koja plugin
		val kojaGradlePlugin = KojaGradlePlugin()
		kojaGradlePlugin.apply(target)

		// Apply in any case
		target.pluginManager.apply("com.google.devtools.ksp")

		// Apply to Kotlin Multiplatform projects
		target.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
			val kotlin = target.extensions.getByType(KotlinMultiplatformExtension::class.java)
			// Add runtime to commonMain
			kotlin.sourceSets.getByName("commonMain").dependencies {
				implementation(runtimeDependency)
			}

			// Add KSP dependency for all Kotlin targets main compilations
			kotlin.targets.configureEach { kotlinTarget ->
				kotlinTarget.compilations.configureEach { compilation ->
					if (compilation.name == "main" && kotlinTarget.name != "metadata") {
						val kspConfig = "ksp${kotlinTarget.name.replaceFirstChar { it.uppercase() }}"
						target.dependencies.add(kspConfig, kspDependency)
					}
					if (compilation.name == "test" && kotlinTarget.name != "metadata") {
						val kspConfig = "ksp${kotlinTarget.name.replaceFirstChar { it.uppercase() }}Test"
						target.dependencies.add(kspConfig, kspDependency)
					}
				}
			}

			kotlin.targets.configureEach { kotlinTarget ->
				kotlinTarget.compilations.configureEach { compilation ->
					val pluginConfigName = "kotlinCompilerPluginClasspath" +
						kotlinTarget.name.replaceFirstChar { it.uppercaseChar() } +
						compilation.name.replaceFirstChar { it.uppercaseChar() }

					val pluginConfig = target.configurations.findByName(pluginConfigName) ?: return@configureEach

					pluginConfig.dependencies.add(
						target.dependencies.create("sh.ondr.koja:koja-compiler:0.4.7"),
					)
				}
			}
		}

		// Apply to pure JVM projects
		target.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
			val kotlinJvm = target.extensions.getByType(KotlinJvmProjectExtension::class.java)
			// Add runtime to main
			kotlinJvm.sourceSets.getByName("main").dependencies {
				implementation(runtimeDependency)
			}

			// Add KSP for main
			target.dependencies.add("ksp", kspDependency)
			// Add KSP for test
			target.dependencies.add("kspTest", kspDependency)
		}
	}

	fun Project.getRuntimeDependency() =
		if (isInternalBuild()) {
			project(":mcp4k-runtime")
		} else {
			"sh.ondr.mcp4k:mcp4k-runtime:${PLUGIN_VERSION}"
		}

	fun Project.getKspDependency() =
		if (isInternalBuild()) {
			project(":mcp4k-ksp")
		} else {
			"sh.ondr.mcp4k:mcp4k-ksp:${PLUGIN_VERSION}"
		}

	fun Project.isInternalBuild() = findProperty("sh.ondr.mcp4k.internal") == "true"

	override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

	override fun getCompilerPluginId(): String = "sh.ondr.mcp4k"

	override fun getPluginArtifact(): SubpluginArtifact =
		SubpluginArtifact(
			groupId = "sh.ondr.mcp4k",
			artifactId = "mcp4k-compiler",
			version = PLUGIN_VERSION,
		)

	override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
		val project = kotlinCompilation.target.project
		val isTestCompilation = (kotlinCompilation.name == "test")

		return project.provider {
			buildList {
				SubpluginOption("enabled", "true")
				SubpluginOption("isTestSet", isTestCompilation.toString())
			}
		}
	}

	private fun Project.recheck() {
		plugins.withType(KotlinBasePluginWrapper::class.java) {
			validateKotlinVersion(it.pluginVersion)
		}
	}

	private fun validateKotlinVersion(actual: String) {
		if (actual != REQUIRED_KOTLIN_VERSION) {
			throw GradleException(
				"mcp4k $PLUGIN_VERSION requires Kotlin $REQUIRED_KOTLIN_VERSION but found $actual. " +
					"Please upgrade the Kotlin Gradle plugin.",
			)
		}
	}

	private fun Project.checkKotlinVersion() {
		var validated = false

		// Runs immediately if the user applied Kotlin before Koja
		plugins.withType(KotlinBasePluginWrapper::class.java) {
			validateKotlinVersion(it.pluginVersion)
			validated = true
		}

		// Runs if Kotlin is applied *after* Koja
		pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") { recheck() }
		pluginManager.withPlugin("org.jetbrains.kotlin.jvm") { recheck() }
		pluginManager.withPlugin("org.jetbrains.kotlin.android") { recheck() }

		afterEvaluate {
			if (!validated) {
				throw GradleException(
					"mcp4k needs the Kotlin plugin $REQUIRED_KOTLIN_VERSION but no Kotlin plugin was applied.",
				)
			}
		}
	}

	private fun Project.checkKspVersion() {
		// Skip version check for internal build
		if (isInternalBuild()) {
			pluginManager.apply("com.google.devtools.ksp")
			return
		}

		// This will run immediately if KSP was already applied, or when it gets applied
		pluginManager.withPlugin("com.google.devtools.ksp") {
			val actual = plugins
				.findPlugin("com.google.devtools.ksp")
				?.resolvedVersion()
				?: "<unknown>"

			if (actual != REQUIRED_KSP_VERSION && actual != "<unknown>") {
				throw GradleException(
					"mcp4k $PLUGIN_VERSION requires KSP $REQUIRED_KSP_VERSION but Gradle resolved $actual. " +
						"Please remove the KSP plugin from your build configuration and let mcp4k handle it.",
				)
			}
		}

		// Apply KSP ourselves (will be a no-op if user already declared it)
		pluginManager.apply("com.google.devtools.ksp")
	}

	private fun Plugin<*>.resolvedVersion(): String? {
		// Extract version from jar filename
		return runCatching {
			val path = javaClass.protectionDomain.codeSource.location.path
			Regex("""symbol-processing-gradle-plugin-(.+?)\.jar""")
				.find(path)
				?.groupValues
				?.get(1)
		}.getOrNull()
	}
}
