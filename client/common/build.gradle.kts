import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	kotlin("multiplatform")
	kotlin("plugin.serialization")
}

group = "dev.eternalbox.client.common"
version = "0.0.1"
java.sourceCompatibility = JavaVersion.VERSION_1_8

val serialization_version = "1.2.0"
val coroutines_version = "1.5.0"

kotlin {
	jvm()
	js() {
		browser()
		nodejs()
	}

	sourceSets {
		val commonMain by getting {
			dependencies {
				implementation(kotlin("stdlib-common"))
				implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization_version")
				implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines_version")

				implementation("dev.brella:kornea-annotations:1.0.5-alpha")
				implementation("dev.brella:kornea-io:5.2.0-alpha")
				implementation("dev.brella:kornea-errors:2.0.3-alpha")
				implementation("dev.brella:kornea-toolkit:3.3.1-alpha")
			}
		}
		jvm().compilations["main"].defaultSourceSet {
			dependencies {
				implementation(kotlin("stdlib-jdk8"))
//				implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serialization_version")
//				implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines_version")
			}
		}
		js().compilations["main"].defaultSourceSet  {
			dependencies {
				implementation(kotlin("stdlib-js"))
//				implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime-js:$serialization_version")
//				implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:$coroutines_version")
			}
		}
	}
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs = listOf("-Xjsr305=strict")
		jvmTarget = "1.8"
	}
}