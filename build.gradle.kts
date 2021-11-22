plugins {
    kotlin("jvm") apply false
    kotlin("multiplatform") apply false
    kotlin("kapt") apply false
    kotlin("plugin.serialization") version "1.6.0" apply false

    id("de.undercouch.download") apply false
}
group = "dev.eternalbox"

allprojects {
    repositories {
        mavenCentral()
        jcenter()
        maven(url = "https://maven.brella.dev")
        maven("https://m2.dv8tion.net/releases")
    }
}