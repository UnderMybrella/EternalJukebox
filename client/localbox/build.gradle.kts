import dev.brella.kornea.gradle.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	kotlin("jvm")
	kotlin("kapt")
}

group = "dev.eternalbox.client.jvm"
version = "0.0.1"
java.sourceCompatibility = JavaVersion.VERSION_1_8

dependencies {
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	implementation(kotlinxCoroutinesModule("core"))

//	implementation("com.sedmelluq:lavaplayer:1.3.75")
	implementation("com.github.walkyst:lavaplayer-fork:1.3.98.4")
	implementation(projectFrom("eternalbox", "client", "common"))
	implementation(projectFrom("eternalbox", "client", "magma"))

	implementation(kotlinxSerialisationModule("json"))
	implementation(ktorModule("client-core-jvm"))
	implementation(ktorModule("client-apache"))
	implementation(ktorModule("client-json-jvm"))
	implementation(ktorModule("client-serialization-jvm"))

	implementation(korneaIOModule())
	implementation(korneaErrorsModule())
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
