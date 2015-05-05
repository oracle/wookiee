# Wookie - QuickStart Guide

This document will help a user getting Wookie up and running quickly using a single service. If you are building multiple services inside of a single Wookie container, you may have to read a little bit more through all the docs. (Because we all know, a good design practice leverages micro services or simple units of logic tied together loosely with an API and/or messages.) This is a step by step guide, so will walk through every single thing needed to get started quickly.

This guide assumes you are using IntelliJ, however others have had success with Eclipse.

#####Step 1 
This step is optional, but can be helpful. We need to build the archetype for Idea so that we can create a baseline service project. To do this you need to build a module based on the service archetype.

1. Click on File -> New Module
2. Select "Maven" and check the "Create from archetype" checkbox
3. Click the Add Archetype button and use the following settings:

    ```
    GroupId = com.webtrends.archetypes
    ArtifactId = wookie-service-archetype
    version = 1.0-SNAPSHOT
    ```
    
4. Select the newly created archetype from the list and click "Next"
5. Uncheck the "inherit" checkboxes if checked and add the following settings:

    ```
    GroupId = com.mycompany
    ArtifactId = MyServiceName
    Version = 1.0-SNAPSHOT
    ```
    
6. Click "Next"
7. Click the "+" button and add a new property "service-name" = "MyServiceName"
8. Click "Next" 
9. Modify in directory settings and click on "Finish"

This will create a base service to start coding in. It will include the dependencies for step 2, so you can skip that step if you created your service using the above method.

#####Step 2
If you built the baseline using the archetype you can skip this step. Add the following dependencies into your pom.xml:
```
<dependency>
    <groupId>com.webtrends</groupId>
    <artifactId>wookie-core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>com.webtrends</groupId>
    <artifactId>wookie-test</artifactId>
    <version>1.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```
This will get the base libraries for the harness.

#####Step 3
Add any component dependencies into your pom.xml and service config. For example if you wanted to include wookie-spray you would add the following to your pom.xml:
```
<dependency>
    <groupId>com.webtrends</groupId>
    <artifactId>wookie-spray</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```
These components will be automatically found and loaded into Wookie when you start it using this method. Nothing further is required.

#####Step 4
Set the internal service to your current service in your service conf.
```
wookie-system {
    service {
        internal = "com.mycompany.service.<SERVICE_CLASS_NAME>"
    }
}
```

#####Step 5 
Execute via the IDE or a command line in your project directory.
```
mvn install
```
#####Step 6 
Add Idea Run/Debug Configuration with following settings:

    * MainClass: com.webtrends.harness.app.HarnessService
    * VM Options: -Dconfig.file=src/main/resources/<CONFIG_NAME>.conf
    * Working Directory: <Root directory for your service>
    * Use classpath of Module: <Choose your service module>

#####Step 7 
Click Run or Debug.

*NOTE:
In your config file, the one that you point to using config.file, you can override any values from any of the components or Wookie itself. For more information on the configurations for Wookie see [Wookie Config](config.md) and for each individual component see the component config docs 

* [wookie-cache-memcache](../components/wookie-cache-memcache/docs/config.md)
* [wookie-cache](../components/wookie-cache/docs/config.md)
* [wookie-cluster](../components/wookie-cluster/docs/config.md)
* [wookie-kafka](../components/wookie-kafka/docs/config.md)
* [wookie-metrics](../components/wookie-metrics/docs/config.md)
* [wookie-netty](../components/wookie-netty/docs/config.md)
* [wookie-sift](../components/wookie-sift/docs/config.md)
* [wookie-socko](../components/wookie-socko/docs/config.md)
* [wookie-spray](../components/wookie-spray/docs/config.md)
* [wookie-zookeeper](../components/wookie-zookeeper/docs/config.md)
