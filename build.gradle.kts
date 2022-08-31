import dev.brella.kornea.gradle.defineVersions

plugins {
    kotlin("jvm") version "1.7.10" apply false
    kotlin("multiplatform") version "1.7.10" apply false
    kotlin("js") version "1.7.10" apply false
    kotlin("plugin.serialization") version "1.7.10" apply false

    id("dev.brella.kornea") version "1.3.0"

//    id("de.undercouch.download") apply false
}
group = "dev.eternalbox"

allprojects {
    repositories {
        mavenCentral()
        maven(url = "https://jitpack.io")
        maven(url = "https://maven.brella.dev")
        maven("https://m2.dv8tion.net/releases")
    }
}

defineVersions {
    ktor("2.1.0")
    kotlinxCoroutines("1.6.4")
    kotlinxSerialisation("1.4.0")
    korneaAnnotations("1.2.0-alpha")
    korneaApollo("1.1.0-alpha")
    korneaErrors("3.1.0-alpha")
    korneaIO("5.5.1-alpha")
    korneaModelling("1.2.0-alpha")
    korneaToolkit("3.5.0-alpha")
}