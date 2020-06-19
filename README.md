ForgeGradle
===========

Minecraft mod development framework used by Forge and FML for the gradle build system

this repository is only for ForgeGradle 1.2, ForgeGradle for minecraft 1.7.2, 1.7.10, and 1.8.

[Here](https://github.com/anatawa12/ForgeGradle-2.3) is repository for ForgeGradle 2.3. If you're modding for 1.12.x, use it.

This project is a fork of [ForgeGradle branch 'FG_1.2'](https://github.com/MinecraftForge/ForgeGradle/tree/FG_1.2).

[Example project found here](https://github.com/anatawa12/ForgeGradle-example)

[Documentation found here](http://forgegradle.readthedocs.org/)

## How to use this ForgeGradle instead of official ForgeGradle

- add jcenter() if not added in repositories in buildscript block.
- replace "net.minecraftforge.gradle:ForgeGradle:1.2-SNAPSHOT" with "com.anatawa12.forge:ForgeGradle:1.2-1.0.+"

if you aren't add any libraries for buildscript, you may able to use buildscript block shown below:

```groovy
buildscript {
    repositories {
        jcenter()
        maven { 
            name = "forge"
            url = "http://files.minecraftforge.net/maven"
        }
        maven {
            name = "sonatype"
            url = "https://oss.sonatype.org/content/repositories/snapshots/"
        }
    }
    dependencies {
        classpath("com.anatawa12.forge:ForgeGradle:1.2-1.0.+") {
            changing = true
        }
    }
}
```
