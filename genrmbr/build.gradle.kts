plugins {
    kotlin("jvm") version "1.8.0"
    alias(libs.plugins.ksp)
    id("maven-publish")
}
dependencies {
    implementation(libs.ksp.symbol.processing)
}

group = "com.github.ATchibo"
version = "0.0.3"

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