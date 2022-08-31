import dev.brella.kornea.gradle.kotlinxCoroutinesModule
import dev.brella.kornea.gradle.projectFrom
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	kotlin("jvm")
	kotlin("kapt")
	kotlin("plugin.serialization")
}

group = "dev.eternalbox.audio.ytdlbox"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_1_8

dependencies {
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	implementation(kotlinxCoroutinesModule("core"))

	implementation("dev.brella.ytdlbox:client:1.2.2")

	implementation(project(":eternalbox-http-client"))

	implementation(projectFrom("eternalbox", "audio", "api"))
	implementation(projectFrom("eternalbox", "audio", "ytm-search"))
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