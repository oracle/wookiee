# Example of Advanced Communications across Multiple Wookiee Instances
In this repo we utilize both wookiee-discovery and wookiee-helidon to create a robust-yet-light discovery and communication system.  
This example will show how to create two Wookiee services: one internal-only that is exposed via wookiee-discovery to be 
messaged via gRPC, and one that is external (with HTTP and WS support) that sends along messages to the internal-only service.

## How to Run
1. Load in the IntelliJ run configurations from the [Run Configurations](../../.idea/runConfigurations) folder.
2. Kick off the 'Example External Wookiee' configuration first, this will expose HTTP GET endpoints
3. Kick off the 'Example Internal Wookiee' configuration second, this will expose a gRPC endpoint
4. Hit the HTTP GET endpoint with a browser or curl, the returned message will have been sent back and forth between the two services
   1. The first HTTP GET endpoint is at http://localhost:8081/external/first-input
   2. The second HTTP GET endpoint is at http://localhost:8081/external/second-input/functional