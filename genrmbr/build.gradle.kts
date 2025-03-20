plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.ksp)
    id("maven-publish")
}
dependencies {
    implementation(libs.ksp.symbol.processing)
}

publishing {
    repositories {
        mavenLocal()
    }
}
