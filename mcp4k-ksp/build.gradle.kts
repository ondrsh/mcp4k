plugins {
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.maven.publish)
}

dependencies {
	implementation(libs.ksp.api)
	testImplementation(gradleTestKit())
	testImplementation(kotlin("test-junit5"))
}

tasks.test {
	useJUnitPlatform()
}
