repositories {
    mavenCentral()
    gradlePluginPortal()
    mavenLocal()
}

plugins {
    `kotlin-dsl`
//    id("org.jetbrains.kotlin.multiplatform") apply false
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.0")
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}