import java.util.zip.ZipInputStream

plugins {
    java
    idea
    eclipse
    `java-gradle-plugin`
    `maven-publish`
}

group = "com.anatawa12.forge"

version = "1.2-${property("version")!!}"

base {
    archivesName.set("ForgeGradle")
}

java {
    targetCompatibility = JavaVersion.VERSION_1_8
    sourceCompatibility = JavaVersion.VERSION_1_8
}

val gradleStartDev = false

repositories {
    mavenCentral()
    maven("https://maven.minecraftforge.net") {
        name = "forge"
    }
    if (gradleStartDev) {
        maven("https://libraries.minecraft.net/") {
            name = "mojang"
        }
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
    implementation(gradleApi())

    // moved to the beginning to be the overrider
    implementation("org.ow2.asm:asm:9.5")
    implementation("org.ow2.asm:asm-tree:9.5")
    implementation("com.google.guava:guava:31.1-jre")

    implementation("com.opencsv:opencsv:5.7.1") // reading CSVs.. also used by SpecialSource
    implementation("com.cloudbees:diff4j:1.3") // for difing and patching
    implementation("com.github.abrarsyed.jastyle:jAstyle:1.2") // formatting
    implementation("net.sf.trove4j:trove4j:2.1.0") // because its awesome.

    implementation("com.github.jponge:lzma-java:1.3") // replaces the LZMA binary
    implementation("com.nothome:javaxdelta:2.0.1") // GDIFF implementation for BinPatches
    implementation("com.google.code.gson:gson:2.10.1") // Used instead of Argo for building changelog.

    implementation("com.anatawa12.forge:SpecialSource:1.11.1") // deobf and reobs

    // because curse
    implementation("org.apache.httpcomponents:httpclient:4.5.14")
    implementation("org.apache.httpcomponents:httpmime:4.5.14")

    // mcp stuff
    implementation("de.oceanlabs.mcp:RetroGuard:3.6.6")
    implementation("de.oceanlabs.mcp:mcinjector:3.2-SNAPSHOT")
    implementation("net.minecraftforge:Srg2Source:4.2.7")

    // pin jdt deps
    // locked means locked due to java 8
    implementation("org.eclipse.jdt:org.eclipse.jdt.core:3.26.0") // locked
    implementation("org.eclipse.platform:org.eclipse.core.commands:3.9.800") // locked
    implementation("org.eclipse.platform:org.eclipse.core.contenttype:3.7.1000") // locked
    implementation("org.eclipse.platform:org.eclipse.core.expressions:3.7.100") // locked
    implementation("org.eclipse.platform:org.eclipse.core.filesystem:1.7.700") // locked
    implementation("org.eclipse.platform:org.eclipse.core.jobs:3.11.0") // locked
    implementation("org.eclipse.platform:org.eclipse.core.resources:3.14.0") // locked
    implementation("org.eclipse.platform:org.eclipse.core.runtime:3.22.0") // locked
    implementation("org.eclipse.platform:org.eclipse.equinox.app:1.5.100") // locked
    implementation("org.eclipse.platform:org.eclipse.equinox.common:3.14.100") // locked
    implementation("org.eclipse.platform:org.eclipse.equinox.preferences:3.9.100") // locked
    implementation("org.eclipse.platform:org.eclipse.equinox.registry:3.10.200") // locked
    implementation("org.eclipse.platform:org.eclipse.osgi:3.18.400")
    implementation("org.eclipse.platform:org.eclipse.text:3.11.0") // locked
    implementation("org.osgi:org.osgi.service.prefs:1.1.2")
    implementation("org.osgi:osgi.annotation:8.1.0")

    // stupid maven
    deployerJars("org.apache.maven.wagon:wagon-ssh:3.5.3")

    //Stuff used in the GradleStart classes
    if (gradleStartDev) {
        compileOnly("com.mojang:authlib:1.5.16")
        compileOnly("net.minecraft:launchwrapper:1.11")
    }

    testImplementation("org.junit.jupiter:junit-jupiter:5.9.3")
}

val compileJava by tasks.getting(JavaCompile::class) {
    options.isDeprecation = true
    //options.compilerArgs += ["-Werror", "-Xlint:unchecked"]
}

val javadoc by tasks.getting(Javadoc::class) {
    // linked javadoc urls.. why not...

    classpath = classpath.filter { !(it.name == "main" && it.parentFile.name == "resources") } 
    val options = options as StandardJavadocDocletOptions
    options.links("https://gradle.org/docs/current/javadoc/")
    options.links("https://guava.dev/releases/18.0/api/docs/")
    options.links("https://asm.ow2.io/javadoc/")
}

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
    useJUnitPlatform()
}

publishing {
    publications {
        val bintray by this.creating(MavenPublication::class) {
            from(components["java"])
            artifactId = base.archivesName.get()

            pom {
                name.set(project.base.archivesName.get())
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
}

gradlePlugin {
    isAutomatedPublishing = false
    plugins {
        create("curseforge") {
            id = "curseforge"
            implementationClass = "net.minecraftforge.gradle.curseforge.CursePlugin"
        }
        create("fml") {
            id = "fml"
            implementationClass = "net.minecraftforge.gradle.user.patch.FmlUserPlugin"
        }
        create("forge") {
            id = "forge"
            implementationClass = "net.minecraftforge.gradle.user.patch.ForgeUserPlugin"
        }
        create("launch4j") {
            id = "launch4j"
            implementationClass = "edu.sc.seis.launch4j.Launch4jPlugin"
        }
        create("liteloader") {
            id = "liteloader"
            implementationClass = "net.minecraftforge.gradle.user.lib.LiteLoaderPlugin"
        }
    }
}

val dependenciesJava8CompatibilityCheck by tasks.creating {
    doLast {
        if (System.getenv("CHECK_JDK_COMPATIBILITY")?.toBoolean() == true) {
            configurations.runtimeClasspath.get().asSequence().forEach {
                val reading = ByteArray(8)
                val zis = ZipInputStream(it.inputStream())
                while (true) {
                    val entry = zis.nextEntry ?: break
                    if (!entry.name.endsWith(".class")) continue
                    if (entry.name == "module-info.class") continue
                    if (entry.name.contains("META-INF/")) continue
                    if (zis.read(reading) != reading.size) continue
                    if (reading[0] == 0xCA.toByte() &&
                        reading[1] == 0xFE.toByte() &&
                        reading[2] == 0xBA.toByte() &&
                        reading[3] == 0xBE.toByte() &&
                        reading[4] == 0x00.toByte() &&
                        reading[5] == 0x00.toByte()) {
                        val major = reading[6].toInt().and(0xFF).shl(8) or reading[7].toInt().and(0xFF)
                        if (major > 52)
                            throw IllegalStateException("${entry.name} of $it is not compatible with java 8 (${major-44}): class ${entry.name}")
                    }
                }
            }
        }
    }
}

tasks.check.get().dependsOn(dependenciesJava8CompatibilityCheck)

// write out version so its convenient for doc deployment
file("build").mkdirs()
file("build/version.txt").writeText("$version")

fun getGitHash(): String {
    val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
        .directory(file("."))
        .start()
    process.waitFor()
    return "-" + (if (process.exitValue() != 0) "unknown" else process.inputStream.reader().use { it.readText() }.trim())
}
