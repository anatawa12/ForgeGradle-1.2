plugins {
    id("com.jfrog.bintray") version "1.8.4"
    java
    idea
    eclipse
    `maven-publish`
    signing
}

group = "com.anatawa12.forge"

if (project.hasProperty("push_release")) {
    version = "1.2-1.0.3"
} else {
    version = "1.2-1.0.4-SNAPSHOT"
}

base {
    archivesBaseName = "ForgeGradle"
}
java {
    targetCompatibility = JavaVersion.VERSION_1_6
    sourceCompatibility = JavaVersion.VERSION_1_6
}

repositories {
    mavenLocal()
    maven("http://files.minecraftforge.net/maven") {
        name = "forge"
    }
    maven("https://repo.eclipse.org/content/groups/eclipse/") {
        // because Srg2Source needs an eclipse dependency.
        name = "eclipse"
    }
    maven("https://oss.sonatype.org/content/repositories/snapshots/") {
        // because SpecialSource doesnt have a full release
        name = "sonatype"
    }
    mavenCentral()
    maven("https://libraries.minecraft.net/") {
        name = "mojang"
    }
}

val jar by tasks.getting(Jar::class) {
    manifest {
        attributes(mapOf(
            "version" to project.version,
            "javaCompliance" to project.java.targetCompatibility,
            "group" to project.group,
            "Implementation-Version" to "${project.version}${getGitHash()}"
        ))
    }
}

val deployerJars by configurations.creating

dependencies {
    compile(gradleApi())

    // moved to the beginning to be the overrider
    compile("org.ow2.asm:asm-debug-all:5.0.3")
    compile("com.google.guava:guava:18.0")

    compile("net.sf.opencsv:opencsv:2.3") // reading CSVs.. also used by SpecialSource
    compile("com.cloudbees:diff4j:1.1") // for difing and patching
    compile("com.github.abrarsyed.jastyle:jAstyle:1.2") // formatting
    compile("net.sf.trove4j:trove4j:2.1.0") // because its awesome.

    compile("com.github.jponge:lzma-java:1.3") // replaces the LZMA binary
    compile("com.nothome:javaxdelta:2.0.1") // GDIFF implementation for BinPatches
    compile("com.google.code.gson:gson:2.2.4") // Used instead of Argo for buuilding changelog.
    compile("com.github.tony19:named-regexp:0.2.3") // 1.7 Named regexp features

    compile("net.md-5:SpecialSource:1.7.3") // deobf and reobs

    // because curse
    compile("org.apache.httpcomponents:httpclient:4.3.3")
    compile("org.apache.httpcomponents:httpmime:4.3.3")

    // mcp stuff
    compile("de.oceanlabs.mcp:RetroGuard:3.6.6")
    compile("de.oceanlabs.mcp:mcinjector:3.2-SNAPSHOT")
    compile("net.minecraftforge.srg2source:Srg2Source:3.2-SNAPSHOT")

    // stupid maven
    deployerJars("org.apache.maven.wagon:wagon-ssh:2.2")

    //Stuff used in the GradleStart classes
    compileOnly("com.mojang:authlib:1.5.16")
    compileOnly("net.minecraft:launchwrapper:1.11")

    testCompile("junit:junit:4.+")
}

val compileJava by tasks.getting(JavaCompile::class) {
    options.isDeprecation = true
    //options.compilerArgs += ["-Werror", "-Xlint:unchecked"]
}

val javadoc by tasks.getting(Javadoc::class) {
    classpath += configurations.compileOnly

    // linked javadoc urls.. why not...

    val options = options as StandardJavadocDocletOptions
    options.links("https://gradle.org/docs/current/javadoc/")
    options.links("http://docs.guava-libraries.googlecode.com/git-history/v18.0/javadoc")
    options.links("http://asm.ow2.org/asm50/javadoc/user/")
}

@Suppress("UnstableApiUsage")
java {
    withJavadocJar()
    withSourcesJar()
}

artifacts {
    archives(jar)
}

val test by tasks.getting(Test::class) {
    if (project.hasProperty("filesmaven")) // disable this test when on the forge jenkins
    {
        exclude("**/ExtensionMcpMappingTest*")
        exclude("**/ExtensionForgeVersionTest*")
    }
}

publishing {
    publications {
        val bintray by this.creating(MavenPublication::class) {
            from(components["java"])

            pom {
                name.set(project.base.archivesBaseName)
                description.set("Gradle plugin for Forge")
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
    repositories {
        maven {
            // change URLs to point to your repos, e.g. http://my.org/repo
            val releasesRepoUrl = "$buildDir/repos/releases"
            val snapshotsRepoUrl = "$buildDir/repos/snapshots"
            url = uri(if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)
        }

        maven {
            name = "mavenCentral"
            url = if (version.toString().endsWith("SNAPSHOT")) uri("https://oss.sonatype.org/content/repositories/snapshots")
            else uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
        }
    }
}

signing {
    sign(publishing.publications["bintray"])
}

if (project.hasProperty("push_release")) {
    bintray {
        user = project.findProperty("BINTRAY_USER")?.toString() ?: ""
        key = project.findProperty("BINTRAY_KEY")?.toString() ?: ""
        setPublications("bintray")

        pkg(closureOf<com.jfrog.bintray.gradle.BintrayExtension.PackageConfig> {
            repo = "maven"
            name = "$group.${project.name}"
            setLicenses("LGPL-2.1")
            websiteUrl = "https://github.com/anatawa12/ForgeGradle-1.2/"
            issueTrackerUrl = "https://github.com/anatawa12/ForgeGradle-1.2/issues"
            vcsUrl = "https://github.com/anatawa12/ForgeGradle-1.2.git"
            publicDownloadNumbers = true
            version.name = "${project.version}"
        })
    }
}

val bintrayUpload by tasks.getting
val assemble by tasks.getting
bintrayUpload.dependsOn(assemble)

// write out version so its convenient for doc deployment
file("build").mkdirs()
file("build/version.txt").writeText("$version")

fun getGitHash(): String {
    val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD").start()
    process.waitFor()
    return "-" + (if (process.exitValue() != 0) "unknown" else process.inputStream.reader().use { it.readText() }.trim())
}
