import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	kotlin("multiplatform")
	kotlin("plugin.serialization")
}

group = "dev.eternalbox"
version = "1.0.0"
java.sourceCompatibility = JavaVersion.VERSION_1_8

val kotlinx_serialisation_version: String by rootProject
val kotlinx_coroutines_version: String by rootProject

kotlin {
	jvm()
	js() {
		browser()
		nodejs()
	}

	sourceSets {
		val commonMain by getting {
			dependencies {
				implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:$kotlinx_serialisation_version")
				implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinx_coroutines_version")
			}
		}

		jvm().compilations["main"].defaultSourceSet {
			dependencies {
//				implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serialization_version")
//				implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines_version")
			}
		}
		js().compilations["main"].defaultSourceSet  {
			dependencies {
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