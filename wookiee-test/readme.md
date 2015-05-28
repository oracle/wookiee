# Wookiee - Test Harness
The Wookiee test harness supplies the primary integration tests for most of the functionality of Wookiee and it's associated components. Some things it cannot test like clustering, as there would only ever be a single node in this kind of testing. And full clustering testing would be required in a full fledged environment. (much like a BVT or QA environment). The way each component would be testing is through a service.

## Test Modes
Wookiee can be tested in two different modes, as a service or as a library. As a service it would require the test system to start up the HarnessService App. As a library a basic harness would be required to setup for the testing purposes. The testing for both service and library however should be identical and only the harness that handles it should be any different.

## What is tested
1. Components
    1. wookiee-audit
    2. wookiee-cache
    3. wookiee-cache-memcache
    4. wookiee-cluster
    5. wookiee-http
    6. wookiee-metrics
    7. wookiee-zookeeper
2. Loading Basic Service
3. Health
4. Logging
5. Basic Messaging