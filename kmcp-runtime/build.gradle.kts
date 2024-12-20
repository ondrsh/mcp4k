plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.maven.publish)
	id("sh.ondr.kmcp") version "0.1.0"
}

kotlin {

	iosArm64()
	iosX64()
	iosSimulatorArm64()
	js(IR) { nodejs() }
	jvm()
	linuxX64()
	macosArm64()

	sourceSets {
		commonMain {
			dependencies {
				implementation(libs.ondrsh.jsonschema)
				implementation(libs.kotlinx.atomicfu)
				implementation(libs.kotlinx.coroutines.core)
				api(libs.kotlinx.serialization.core)
				api(libs.kotlinx.serialization.json)
			}
		}
		commonTest {
			dependencies {
				implementation(libs.kotlinx.coroutines.test)
				implementation(kotlin("test"))
			}
		}
	}
}
