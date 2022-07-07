# Wookie - Development Environment Setup

## Purpose

The purpose of this doc is aggregate notes and processes in setting up development environment for services to
the harness.

## Tools and Packages

* Scala – 2.11.x
* Install apache-maven via sudo apt-get install maven //at present this should install maven3, as of last update.
	We had issues using maven v2 with archetypes which were resolved using maven v3.
* Git
* Java 1.7, don't forget to set JAVA_HOME if using different versions of java
* IntelliJ Idea 13.x or later (recommended)

## Where's the Source

As of version **1.0**, Wookiee code is now located in our GitHub public repository for [Wookiee](https://github.com/oracle/wookiee).
You can fork this code into your own repository and work with it from there.

## Compiling Wookiee

- Supported Profiles 
	* core (default)
	* dev (needed for setting scala version)
	* all
	* examples
	* components
	* archetypes

1.  Go to the relevant source directory
2.  mvn compile // this compiles the project
3.  mvn install // will compile project and place artifact in local maven repository
3.  mvn package //compile and runs tests first then creates the desired jar and tar.gz bundle.

### Alternatively, this can be done via IntelliJ Idea:

1. Open IntelliJ Idea, and start any project
2. Use the maven plugin in Idea to add the pom.xml to your project.  Idea will automagically download the dependencies
you need into the your local repository.
3. Go to the Wookiee project and click "install" and this will compile, test, package, and move the jar into your
local repository.

## Compiling Example Service

1.  Go to relevant directory
2.  mvn package or mvn compile

## Installing Example Service inside Wookiee and IDE Debugging

To install a service inside of Wookiee for production, please refer to the packaging documentation (Coming soon). For development deploying a service is done through a bash script found in wookiee-core/bin. This script will simply create symbolic links to the service jar, the service library folder and the service configuration file. The script expects that you are using the default setup, which is the following:

* Your services directory is contained withing your wookiee-core folder
* You service is built according to the service archetype in this repo

To execute the script you would do the following ```./deployService.sh example-rest ~/src/wookiee/examples/example-rest```
For more information see [Bash Scripts](../wookiee-core/bin/readme.md)

