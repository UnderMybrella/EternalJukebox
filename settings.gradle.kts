pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven ("https://dl.bintray.com/kotlin/kotlin-eap")
    }
}

fun includeSubprojects(rootName: String, rename: (File) -> String = { "$rootName-${it.name}"} ) {
    file(rootName)
        .listFiles(File::isDirectory)
        ?.forEach { dir ->
            include(":$rootName:${dir.name}")
            project(":$rootName:${dir.name}").name = rename(dir)
        }
}

rootProject.name = "eternalbox"

includeSubprojects("analysis")
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

include(":server:core")

//include(":storage:api")
//include(":storage:base")

enableFeaturePreview("GRADLE_METADATA")