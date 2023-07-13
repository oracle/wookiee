# Wookiee - QuickStart Guide

This document will help a user getting Wookiee up and running quickly using a single service. If you are building multiple services inside of a single Wookiee container, you may have to read a little bit more through all the docs. (Because we all know, a good design practice leverages micro services or simple units of logic tied together loosely with an API and/or messages.) This is a step by step guide, so will walk through every single thing needed to get started quickly.

This guide assumes you are using IntelliJ, however others have had success with Eclipse.

1. Add Wookiee Imports for Core Codebase
```xml
<dependency>
    <groupId>com.oracle.infy</groupId>
    <artifactId>wookiee-core_${scala.artifact.version}</artifactId>
    <version>${wookiee.version}</version>
</dependency>
<dependency>
    <groupId>com.oracle.infy</groupId>
    <artifactId>wookiee-test_${scala.artifact.version}</artifactId>
    <version>${wookiee.version}</version>
    <scope>test</scope>
</dependency>
```
```sbt
libraryDependencies += "com.oracle.infy" %% "wookiee-core" % "${wookiee.version}"
libraryDependencies += "com.oracle.infy" %% "wookiee-test" % "${wookiee.version}"
```
    
2. Create a class that extends the Wookiee ServiceV2 interface
   See an example here: [BasicService](../examples/basic-service/src/main/scala/com/oracle/infy/qa/BasicService.scala)
```scala
package my.company.service

class MyServiceName(config: Config) extends ServiceV2(config) {
   override def start(): Unit = {
     // Do any startup logic here
   }

   override def prepareForShutdown(): Unit = { 
     // Do any shutdown logic here
   }
}
```
3. Create a configuration file for your service. See an example here: [application.conf](../examples/basic-service/src/main/resources/application.conf)
```hocon
wookiee-system {
  services {
    internal = "my.company.service.MyServiceName"
  }
}
```

4. Create a debug configuration to run the Wookiee Service
* Main Class: com.oracle.infy.wookiee.app.HarnessService
* VM Options: -Dconfig.file=<Location of your application.conf file>
* Working Directory: <Root directory for your service>
* Use classpath of Module: <Choose your service module>
* JRE: <Choose your JRE above 1.11>

5. Run the debug configuration, you should see Wookiee come up. You can now hit your service at http://localhost:8080/healthcheck

6. To begin real development, start filling in your Wookiee Service class. Or begin to add additional Components for your service. 
```xml
# This component enables HTTP and Websocket hosting
<dependency>
    <groupId>com.oracle.infy</groupId>
    <artifactId>wookiee-helidon_${scala.artifact.version}</artifactId>
    <version>${wookiee.version}</version>
</dependency>
```
These components will be automatically found and loaded into Wookiee when you start it using these dependencies. Nothing further is required.