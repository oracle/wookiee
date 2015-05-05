# Wookie - Test Harness
The Wookie test harness supplies the primary integration tests for most of the functionality of Wookie and it's associated components. Some things it cannot test like clustering, as there would only ever be a single node in this kind of testing. And full clustering testing would be required in a full fledged environment. (much like a BVT or QA environment). The way each component would be testing is through a service.

## Test Modes
Wookie can be tested in two different modes, as a service or as a library. As a service it would require the test system to start up the HarnessService App. As a library a basic harness would be required to setup for the testing purposes. The testing for both service and library however should be identical and only the harness that handles it should be any different.

## What is tested
1. Components
    1. wookie-audit
    2. wookie-cache
    3. wookie-cache-memcache
    4. wookie-cluster
    5. wookie-http
    6. wookie-metrics
    7. wookie-zookeeper
2. Loading Basic Service
3. Health
4. Logging
5. Basic Messaging