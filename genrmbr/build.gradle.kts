plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.ksp)
    id("maven-publish")
}
dependencies {
    implementation(libs.ksp.symbol.processing)
}

group = "com.github.ATchibo"
version = "0.0.3"