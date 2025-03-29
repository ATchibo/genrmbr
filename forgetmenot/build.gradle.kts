plugins {
    kotlin("jvm") version "1.8.0"
    alias(libs.plugins.ksp)
    id("maven-publish")
}
dependencies {
    implementation(libs.ksp.symbol.processing)
    implementation("com.squareup:kotlinpoet:2.1.0")
    implementation("com.squareup:kotlinpoet-ksp:2.1.0")
}

group = "com.github.ATchibo"
version = "1.0"

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()

            from(components["java"])
        }
    }
}