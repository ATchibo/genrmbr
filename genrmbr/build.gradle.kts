plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.ksp)
}
dependencies {
    implementation(libs.ksp.symbol.processing)
}
