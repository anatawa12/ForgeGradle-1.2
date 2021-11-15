# contribution Guide

## Building this project

To build this project, it's required to install git.
Please install git commandline tool.

To build this project, what you have to run is ``./gradlew build``.
But it's not enough to use this ForgeGradle for some project.
This ForgeGradle is depending on a separated jar which built 
from separated directory/project in this repository.
So you should publish to some maven repository.
You may publish to maven local repository by ``./gradlew publishToMavenLocal``.
