pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven ("https://dl.bintray.com/kotlin/kotlin-eap")
    }
}

rootProject.name = "eternalbox"

include(":analysis:api")
include(":analysis:spotify")

include(":common")

include(":client:common")
include(":client:magma")
include(":client:localbox")
include(":client:eternalbot")

include(":server")

enableFeaturePreview("GRADLE_METADATA")