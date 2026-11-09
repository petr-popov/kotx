plugins {
    kotlin("jvm") apply false
    kotlin("plugin.serialization") apply false
}

allprojects {
    group = "io.kotx"
    version = "1.2.0"  // Release версия (без SNAPSHOT)

    repositories {
        mavenLocal()
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    afterEvaluate {
        configure<PublishingExtension> {
            publications {
                create<MavenPublication>("mavenJava") {
                    artifactId = "kotx-${project.name}"
                    from(components["java"])
                    pom {
                        url.set("https://kotx.io")
                        licenses {
                            license {
                                name.set("The Apache License, Version 2.0")
                                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                            }
                        }
                        developers {
                            developer {
                                id.set("petr-popov")
                                name.set("Petr Popov")
                                email.set("petr.popov88@gmail.com")
                                url.set("https://github.com/petr-popov")
                            }
                        }
                    }
                }
            }
        }
    }
}

tasks.register("publishAll") {
    group = "publishing"
    description = "Publishes all kotx modules to mavenLocal"
    dependsOn(
        ":core:publishMavenJavaPublicationToMavenLocal",
        ":mybatis:publishMavenJavaPublicationToMavenLocal",
        ":test:publishMavenJavaPublicationToMavenLocal",
        ":dbq:publishMavenJavaPublicationToMavenLocal",
    )
}

tasks.register("publishAllPublicationsToNexusRepository") {
    group = "publishing"
    description = "Publishes all kotx module publications to the Nexus repository"
    dependsOn(
        ":core:publishAllPublicationsToNexusRepository",
        ":mybatis:publishAllPublicationsToNexusRepository",
        ":test:publishAllPublicationsToNexusRepository",
        ":dbq:publishAllPublicationsToNexusRepository",
    )
}
