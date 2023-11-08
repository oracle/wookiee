package com.oracle.infy.qa;

import com.oracle.infy.wookiee.service.ServiceV2;
import com.typesafe.config.Config;

class BasicService extends ServiceV2 {
    /**
     * Constructs a new object.
     */
    public BasicService(Config config) {
        super(config);
    }
}
