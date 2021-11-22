import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	kotlin("multiplatform")
	kotlin("plugin.serialization")
}

group = "dev.eternalbox"
version = "1.0.0"
java.sourceCompatibility = JavaVersion.VERSION_1_8

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
				api("io.ktor:ktor-client-cio:$KTOR_VERSION")
				api("io.ktor:ktor-client-serialization:$KTOR_VERSION") {
					exclude("org.jetbrains.kotlinx", "kotlinx-serialization-json")
				}
				api("io.ktor:ktor-client-encoding:$KTOR_VERSION")
				api("org.jetbrains.kotlinx:kotlinx-serialization-json:$KOTLINX_SERIALISATION_VERSION")
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