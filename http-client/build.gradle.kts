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
val ktor_version: String by rootProject

kotlin {
	jvm()
//	js() {
//		browser()
//		nodejs()
//	}

	sourceSets {
		val commonMain by getting {
			dependencies {
				api(project(":common"))
			}
		}

		jvm().compilations["main"].defaultSourceSet {
			dependencies {
				api("io.ktor:ktor-client-cio:$ktor_version")
				api("io.ktor:ktor-client-serialization:$ktor_version")
				api("io.ktor:ktor-client-encoding:$ktor_version")
			}
		}
//		js().compilations["main"].defaultSourceSet  {
//			dependencies {
//			}
//		}
	}
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs = listOf("-Xjsr305=strict")
		jvmTarget = "1.8"
	}
}