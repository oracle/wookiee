## Development Bash Scripts
In this bin directory there are a couple of bash scripts that help a developer to easily get his environment setup for running the harness after it has been compiled.

### deployService.sh
This script will deploy a service that you have built into the current harness. It will deploy into default path locations, so if you don't change that then it will automatically work, however if you do change those then you will need to edit this script to take that into account. It creates symbolic links so that if you compile you services it will automatically pick up the changes and after you restart the harness it will pick up those changes. You only need to run this once for a service, unless you remove the directory that is created for this service. In which case you would simply need to run it again.
Usage:
```deployService.sh [SERVICE_NAME] [SERVICE_LOCATION]```

* SERVICE_NAME - The name of your service. If you follow the guideline for creating services this script will work as expected. The name of the service is basically the name that the folder will be called that will contain all the service libs and config files
* SERVICE_LOCATION - The root folder for your service.

### setDevComponents.sh
This will deploy all the components to the components directory in the harness. It will basically create symbolic links. You only need to do this once, and it will deploy all the components to your harness. By default all components are disabled, so if you just start up the harness at this point it will note that it did not start up any components due to them being disabled. This script can also build all the components that are stored in the components directory by simply running the command with a "build" parameter. For a clean build use the "clean" parameter.
Usage:
```setDevComponents.sh [build|clean]```

### updateDevComponents.sh
This will enable or disable components as needed. Basically it will modify the config file for the component and set the value to true or false.
Usage:
```updateDevComponents.sh [ComponentName]=[TRUE|FALSE] ...```
Example:
```updateDevComponents.sh wookie-http=true wookie-netty=false```
The above example will simply enable wookie-http and disable wookie-netty

Update dev components can also be used to update all the components to a single value. So if you use the following:
```updateDevComponents.sh all=false```
Then all the components will be disabled. Same applies for the true value.

### updeployService.sh
This simply removes the service from the harness, which under the covers simply does a delete, which ultimately simply removes the symbolic links
Usage:
```undeployService.sh [SERVICE_NAME]```

* SERVICE_NAME - basically same variable as defined in the deployService.sh script.