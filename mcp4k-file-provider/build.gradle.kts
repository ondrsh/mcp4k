import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.maven.publish)
	alias(libs.plugins.dokka)
}

kotlin {
	js(IR) {
		nodejs()
		binaries.library()
	}
	jvm()

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
				api(project(":mcp4k-runtime"))
				implementation(libs.square.okio)
				implementation(libs.kotlinx.coroutines.core)
			}
		}
		commonTest {
			dependencies {
				implementation(kotlin("test"))
				implementation(project(":mcp4k-test"))
				implementation(libs.kotlinx.coroutines.test)
				implementation(libs.square.okio.fakefilesystem)
				implementation(libs.kotlinx.serialization.json)
			}
		}
	}
}

mavenPublishing {
	configure(KotlinMultiplatform(javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml")))
}
