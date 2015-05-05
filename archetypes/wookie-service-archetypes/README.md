# Wookie - Creating a Service

For development of single service use the steps for Intellij at the bottom of this doc and then refer to the quickstart guide for [Wookie QuickStart](../../docs/quickstart.md)

## Overview

This document contains information on how to create a simple maven project for a Wookie service. It will use Maven archetypes to layout a basic project. It also sets out a design pattern that should be adhered to when building a new service, and giving a basic understanding of the underlying support structure that the service leverages.

## Service Design Patterns
The goal around services is simply that a developer should not need to understand the underlying structure that is used (ie. the components), but allows the developer to focus on the business logic of the problem at hand. So a developer should not have to figure out how a http request is going to get to his business logic, they simply know that they want a specific path to eventually call a function where their business logic lives handling the marshalling and unmarshalling of objects. The service design pattern is a little less strict than the component design pattern and is purposely so as to give the developer the greatest flexibility. 

## Deploying Dev Wookie Service
Deployment for a Wookie service during development is built to be as easy as possible. As long as a user sticks to the design patterns deploying within a development environment should be seamless. Once you have downloaded the code from GIT for Wookie there is a directory <WOOKIE_ROOT_PATH>/wookie-core/bin, which contain a number of bash shell scripts.

* ./setDevComponents.sh - used for to deploy components to a dev environment
* ./deployService.sh - used to deploy a service to a dev environment
* ./undeployService.sh - simply deletes the directory that contained the previously deployed service. Not the service development code.

To deploy a service you simply need to execute the deployService.sh bash shell script. This script will create symbolic links to the original project and when you start up Wookie, it will start up the services that are found. Because these are symbolic links you should only need to do this once initially, unless you delete the symbolic links.

Usage: ./deployService.sh [SERVICE_NAME] [SERVICE_LOCATION]
  * SERVICE_NAME - the name of your service, this would be basically the name found in the jar that is built for your service like example-caching-1.0-SNAPSHOT.jar, so the service name in that case would be example-caching
  * SERVICE_LOCATION - the root directory for you service, so sub directories should include "src" and "target" after compilation.

## IntelliJ (Idea)

In order to create a component using IntelliJ (12.x or later), you should use the following steps: (Steps are based on 14.x however are very similar in 12.x and 13.x)

1. Select "File"->"New Module" or "New Project".
2. Select the "Maven" module/project type.
3. On the same screen click the "Create from archetype" checkbox and select our archetype `com.webtrends.archetypes:wookie-service-archetype`.
4. If the archetype does not exist in the list, click "Add archetype".  Enter `com.webtrends.archetypes` for the group id, `wookie-service-archetype` for the artifact id, and some version number (e.g. "1.0-SNAPSHOT") for the version.
5. If you needed to add, click OK.

6. Click Next and then insert `com.webtrends.service` for the GroupId and the name of the new service you are creating for ArtifactId
7. Click Next.
8. Click the `+` sign in the properties box and add the following property `service-name` with the value that will equal the name of your new service. (This will be the name of the primary class file)
9. Click Next.
10. Insert the Module name and locations where you want to place the module/project
11. Click Finish.
12. Once everything is loaded, click on the refresh button for your maven service to import sources.
13. Add files to git as required.

