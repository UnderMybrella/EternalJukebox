import dev.brella.kornea.gradle.kotlinxCoroutinesModule
import dev.brella.kornea.gradle.ktorModule
import dev.brella.kornea.gradle.projectFrom
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	kotlin("jvm")
	kotlin("kapt")
}

group = "dev.eternalbox"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11

dependencies {
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	implementation(kotlinxCoroutinesModule("core"))

	implementation(ktorModule("server-netty"))
	implementation(ktorModule("serialization"))

	implementation(projectFrom("eternalbox", "analysis", "api"))
	implementation(projectFrom("eternalbox", "analysis", "spotify"))

	implementation(projectFrom("eternalbox", "audio", "api"))
	implementation(projectFrom("eternalbox", "audio", "ytdlbox"))

//	implementation(project(":audio:api"))
//	implementation(project(":audio:ytdlbox"))
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs = listOf("-Xjsr305=strict")
		jvmTarget = "11"
	}
}