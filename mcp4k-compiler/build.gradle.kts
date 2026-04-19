
plugins {
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.maven.publish)
}

dependencies {
	compileOnly(libs.kotlin.compiler.embeddable)
	testImplementation(libs.kotlin.compiler.embeddable)
}
