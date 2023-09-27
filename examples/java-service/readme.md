# Wookiee Examples - Basic Java Service
A one line Java Service!! This is an example of how easy it is to get your project running
on Wookiee. This one line service can be run just like it would be on a server.

## Running Service Locally
If you want to see what it looks like to have a Wookiee service running, then open
this repo up in IntelliJ Idea and do the following...

* Right click on pom.xml in ../examples/java-service and click "Add as Maven Project"
    * This adds the example repo and its code as a module to your project
* Edit Run/Debug Configurations
* Hit the "+" and select Application
* Input the Following:
    * Main Class: com.oracle.infy.wookiee.app.HarnessService
    * VM Options: -Dconfig.file=src/main/resources/application.conf -Dlogback.configurationFile=src/main/resources/logback.xml
    * Working Directory: ${your path to ../wookiee/examples/java-service}
    * Use Classpath of Module: java-service
    * JRE: 1.11 or later
* Press "OK"

You can then take advantage of Wookiee's health checks:
* [Full Healthchecks](http://localhost:8080/healthcheck)
* [Load Balancer Healthchecks](http://localhost:8080/healthcheck/lb)
* [Nagios Healthchecks](http://localhost:8080/healthcheck/nagios)
