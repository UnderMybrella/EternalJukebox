import dev.brella.kornea.gradle.kotlinxCoroutinesModule
import dev.brella.kornea.gradle.projectFrom
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
	implementation(kotlinxCoroutinesModule("core"))

	implementation(project(":eternalbox-http-client"))

	api(projectFrom("eternalbox", "analysis", "api"))
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