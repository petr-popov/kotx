plugins {
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":mybatis"))
    api("io.micrometer:micrometer-core:1.13.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.3.0")
}
