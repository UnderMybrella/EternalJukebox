import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val ktor_version: String by rootProject
val kotlin_version: String by rootProject
val coroutines_version: String by rootProject
val logback_version: String by rootProject

plugins {
	kotlin("jvm")
	kotlin("kapt")
}

group = "dev.eternalbox"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11

val kotlinx_coroutines_version: String by rootProject

dependencies {
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinx_coroutines_version")

	implementation(project(":analysis:api"))
	implementation(project(":analysis:spotify"))
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

for (analysisType in listOf("")) {

}