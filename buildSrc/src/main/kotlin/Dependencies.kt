import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.kotlin.dsl.project

const val KOTLIN_VERSION="1.6.0"
const val KOTLINX_COROUTINES_VERSION="1.5.2"
const val KOTLINX_SERIALISATION_VERSION="1.3.1"
const val KTOR_VERSION="1.6.5"
const val LOGBACK_VERSION="1.2.3"

inline fun DependencyHandler.analysisProject(module: String) =
    projectFrom("analysis", module)
inline fun DependencyHandler.audioProject(module: String) =
    projectFrom("audio", module)
inline fun DependencyHandler.storageProject(module: String) =
    projectFrom("storage", module)
inline fun DependencyHandler.clientProject(module: String) =
    projectFrom("client", module)
inline fun DependencyHandler.projectFrom(parent: String, module: String) =
    project(":$parent:$parent-$module")

inline fun Project.defineSourceSet(newName: String, dependsOn: List<String>, noinline includedIn: (String) -> Boolean) =
    project.extensions.getByType<KotlinMultiplatformExtension>()
        .defineSourceSet(newName, dependsOn, includedIn)

fun KotlinMultiplatformExtension.defineSourceSet(
    newName: String,
    dependsOn: String,
    includedIn: List<String>,
    config: (KotlinSourceSet.() -> Unit)? = null
) =
    defineSourceSet(newName, listOf(dependsOn), { it in includedIn }, config)

fun KotlinMultiplatformExtension.defineSourceSet(
    newName: String,
    dependsOn: List<String>,
    includedIn: List<String>,
    config: (KotlinSourceSet.() -> Unit)? = null
) =
    defineSourceSet(newName, dependsOn, { it in includedIn }, config)

fun KotlinMultiplatformExtension.defineSourceSet(
    newName: String,
    dependsOn: String,
    vararg includedIn: String,
    config: (KotlinSourceSet.() -> Unit)? = null
) =
    defineSourceSet(newName, listOf(dependsOn), { it in includedIn }, config)

fun KotlinMultiplatformExtension.defineSourceSet(
    newName: String,
    dependsOn: List<String>,
    vararg includedIn: String,
    config: (KotlinSourceSet.() -> Unit)? = null
) =
    defineSourceSet(newName, dependsOn, { it in includedIn }, config)

fun KotlinMultiplatformExtension.defineSourceSet(
    newName: String,
    dependsOn: String,
    includedIn: String,
    config: (KotlinSourceSet.() -> Unit)? = null
) =
    defineSourceSet(newName, listOf(dependsOn), { includedIn == it }, config)

fun KotlinMultiplatformExtension.defineSourceSet(
    newName: String,
    dependsOn: List<String>,
    includedIn: String,
    config: (KotlinSourceSet.() -> Unit)? = null
) =
    defineSourceSet(newName, dependsOn, { includedIn == it }, config)

fun KotlinMultiplatformExtension.defineSourceSet(
    newName: String,
    dependsOn: String,
    config: (KotlinSourceSet.() -> Unit)? = null
) =
    defineSourceSet(newName, listOf(dependsOn), null, config)

fun KotlinMultiplatformExtension.defineSourceSet(
    newName: String,
    dependsOn: List<String>,
    config: (KotlinSourceSet.() -> Unit)? = null
) =
    defineSourceSet(newName, dependsOn, null, config)

fun KotlinMultiplatformExtension.defineSourceSet(
    newName: String,
    dependsOn: List<String>,
    includedIn: ((String) -> Boolean)? = null,
    config: (KotlinSourceSet.() -> Unit)? = null
) {
    for (suffix in listOf("Main", "Test")) {
        val newSourceSet = sourceSets.maybeCreate("$newName$suffix")
        dependsOn.forEach { dep -> newSourceSet.dependsOn(sourceSets["$dep$suffix"]) }
        sourceSets.forEach { currentSourceSet ->
            val currentName = currentSourceSet.name
            if (currentName.endsWith(suffix)) {
                val prefix = currentName.removeSuffix(suffix)
                if (includedIn?.invoke(prefix) == true) currentSourceSet.dependsOn(newSourceSet)
            }
        }

        config?.invoke(newSourceSet)
    }
}

//fun KotlinMultiplatformExtension.addCompilerArgs(vararg compilerArgs: String) =
//    targets.all {
//        compilations.all {
//            kotlinOptions {
//                freeCompilerArgs = freeCompilerArgs + compilerArgs
//            }
//        }
//    }

inline fun Project.multiplatform(noinline configuration: KotlinMultiplatformExtension.() -> Unit): Unit =
    configure(configuration)

//inline fun