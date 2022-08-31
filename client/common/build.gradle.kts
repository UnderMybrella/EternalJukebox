import dev.brella.kornea.gradle.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	kotlin("multiplatform")
	kotlin("plugin.serialization")
}

group = "dev.eternalbox.client.common"
version = "0.0.1"
java.sourceCompatibility = JavaVersion.VERSION_1_8

kotlin {
	jvm()
	js(BOTH) {
		browser()
		nodejs()
	}

	sourceSets {
		val commonMain by getting {
			dependencies {
				implementation(kotlinxSerialisationModule("json"))
				implementation(kotlinxCoroutinesModule("core"))

				implementation(korneaAnnotationsModule())
				implementation(korneaIOModule())
				implementation(korneaErrorsModule())
				implementation(korneaToolkitModule())
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