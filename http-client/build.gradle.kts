import dev.brella.kornea.gradle.kotlinxSerialisationModule
import dev.brella.kornea.gradle.ktorModule
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
				api(project(":eternalbox-common"))
			}
		}

		jvm().compilations["main"].defaultSourceSet {
			dependencies {
				api(ktorModule("client-cio"))
				api(ktorModule("client-serialization")) {
					exclude("org.jetbrains.kotlinx", "kotlinx-serialization-json")
				}
				api(ktorModule("client-encoding"))
				api(kotlinxSerialisationModule("json"))
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