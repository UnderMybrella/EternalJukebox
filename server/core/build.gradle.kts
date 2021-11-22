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
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$KOTLINX_COROUTINES_VERSION")

	implementation("io.ktor:ktor-server-netty:$KTOR_VERSION")
	implementation("io.ktor:ktor-serialization:$KTOR_VERSION")

	implementation(analysisProject("api"))
	implementation(analysisProject("spotify"))

	implementation(audioProject("api"))
	implementation(audioProject("ytdlbox"))

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