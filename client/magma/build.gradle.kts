import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	kotlin("jvm")
	kotlin("kapt")
}

group = "dev.eternalbox.client.jvm.magma"
version = "0.0.1"
java.sourceCompatibility = JavaVersion.VERSION_1_8

dependencies {
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.0")

	implementation("com.sedmelluq:lavaplayer:1.3.75")

	implementation(project(":client:common"))

	implementation("dev.brella:kornea-io:5.2.0-alpha")
	implementation("dev.brella:kornea-errors:2.0.3-alpha")

//	implementation("club.minnced:opus-java-api:1.0.5")
//	implementation("club.minnced:opus-java-natives:1.0.5")
//	implementation("net.java.dev.jna:jna:4.4.0")
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
