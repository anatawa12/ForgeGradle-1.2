plugins {
    java
    `maven-publish`
}

group = project(":").group
version = project(":").version

@Suppress("UnstableApiUsage")
java {
    withJavadocJar()
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.ow2.asm:asm-debug-all:5.0.3")
    implementation("com.google.guava:guava:18.0")
}

publishing {
    publications {
        val bintray by this.creating(MavenPublication::class) {
            from(components["java"])
            artifactId = base.archivesBaseName

            pom {
                name.set(project.base.archivesBaseName)
                description.set("Gradle plugin for Forge, a separated part because of dependencies")
                url.set("https://github.com/anatawa12/ForgeGradle-1.2")

                scm {
                    url.set("https://github.com/anatawa12/ForgeGradle-1.2")
                    connection.set("scm:git:git://github.com/anatawa12/ForgeGradle-1.2.git")
                    developerConnection.set("scm:git:git@github.com:anatawa12/ForgeGradle-1.2.git")
                }

                issueManagement {
                    system.set("github")
                    url.set("https://github.com/anatawa12/ForgeGradle-1.2/issues")
                }

                licenses {
                    license {
                        name.set("Lesser GNU Public License, Version 2.1")
                        url.set("https://www.gnu.org/licenses/lgpl-2.1.html")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("AbrarSyed")
                        name.set("Abrar Syed")
                        roles.set(setOf("developer"))
                    }

                    developer {
                        id.set("LexManos")
                        name.set("Lex Manos")
                        roles.set(setOf("developer"))
                    }

                    developer {
                        id.set("anatawa12")
                        name.set("anatawa12")
                        roles.set(setOf("developer"))
                    }
                }
            }
        }
    }
}
