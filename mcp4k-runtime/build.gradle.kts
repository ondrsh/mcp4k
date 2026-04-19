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
				implementation(libs.kotlinx.atomicfu)
				implementation(libs.kotlinx.coroutines.core)
				implementation(libs.kotlinx.serialization.core)
				implementation(libs.kotlinx.serialization.json)
				implementation(libs.koja.runtime)
			}
		}
	}
}

mavenPublishing {
	configure(KotlinMultiplatform(javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml")))
}
