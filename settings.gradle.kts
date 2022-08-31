pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven ("https://dl.bintray.com/kotlin/kotlin-eap")
    }
}

/*fun includeSubprojects(rootName: String, rename: (File) -> String = { "$rootName-${it.name}"} ) {
    file(rootName)
        .listFiles(File::isDirectory)
        ?.forEach { dir ->
            include(":$rootName:${dir.name}")
            project(":$rootName:${dir.name}").name = rename(dir)
        }
}*/

rootProject.name = "eternalbox"

/*includeSubprojects("analysis")
includeSubprojects("audio")
includeSubprojects("client")
includeSubprojects("storage")

include(":common")
//
//include(":client:common")
//include(":client:magma")
//include(":client:localbox")
//include(":client:eternalbot")

include(":http-client")

include(":server:core")*/

//include(":storage:api")
//include(":storage:base")

fun includeSubprojects(path: List<String>, dir: File) {
    dir.listFiles(File::isDirectory)
        ?.forEach { projectDir ->
            if (projectDir.name.equals("buildSrc", true)) return@forEach

            val newPath = path + projectDir.name
            if (File(projectDir, "build.gradle").exists() || File(projectDir, "build.gradle.kts").exists()) {
                val pathName = newPath.joinToString(":", prefix = ":")
                val projectName = newPath.joinToString("-", prefix = "${rootProject.name}-")
                include(pathName)
                project(pathName).name = projectName

                println("Loading $projectName @ $pathName")
            }

            includeSubprojects(newPath, projectDir)
        }
}

includeSubprojects(emptyList(), rootDir)