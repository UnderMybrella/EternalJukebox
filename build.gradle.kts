plugins {
    kotlin("jvm") version "1.5.0" apply false
    kotlin("multiplatform") version "1.5.0" apply false
    kotlin("kapt") version "1.5.0" apply false
    kotlin("plugin.serialization") version "1.5.0" apply false

    id("de.undercouch.download") version "4.1.1" apply false
}
group = "dev.eternalbox"

allprojects {
    repositories {
        mavenCentral()
        jcenter()
        maven(url = "https://maven.brella.dev")
        maven("https://dl.bintray.com/kotlin/kotlin-eap")
        maven("https://kotlin.bintray.com/kotlinx")

        maven("https://m2.dv8tion.net/releases")
    }
}