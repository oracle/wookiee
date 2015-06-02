# Wookiee - Component: Metrics

For Configuration information see [Metrics Config](docs/config.md)

The metrics component allows users to track metrics for functionality on their services. It is based off the [codahale github project](http://www.github.com/codahale/metrics). It includes the following metrics:

* Counter - A metric that simply counts the number of time some event has occurred.
* Gauge - Instantaneous measurements of something
* Timer - Times how long a specific event takes to complete
* Histogram - Tracks the distribution of a stream of values.
* Meter - Marks the occurrence of an event

For a working example of how this would work see [Wookiee - Metrics Example](../../examples/example-metrics)