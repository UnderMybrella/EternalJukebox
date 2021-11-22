import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	kotlin("jvm")
	kotlin("kapt")
	kotlin("plugin.serialization")
}

group = "dev.eternalbox"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_1_8


dependencies {
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$KOTLINX_COROUTINES_VERSION")

	implementation(project(":http-client"))

	api(analysisProject("api"))
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs = listOf("-Xjsr305=strict")
		jvmTarget = "1.8"
	}
}