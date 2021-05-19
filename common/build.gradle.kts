import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	kotlin("multiplatform")
	kotlin("plugin.serialization")
}

group = "dev.eternalbox.client.common"
version = "1.0.0"
java.sourceCompatibility = JavaVersion.VERSION_1_8

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
				implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$KOTLINX_SERIALISATION_VERSION")
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