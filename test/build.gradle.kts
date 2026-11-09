dependencies {
    api(project(":core"))
    api(project(":mybatis"))
    api("io.kotest:kotest-assertions-core:6.1.3")

    implementation("org.postgresql:postgresql:42.7.11")
    implementation("com.zaxxer:HikariCP:7.0.2")
    implementation("org.testcontainers:postgresql:1.21.4")
    implementation("io.ktor:ktor-client-cio:3.4.0")
    implementation("com.opencsv:opencsv:5.12.0")
}
