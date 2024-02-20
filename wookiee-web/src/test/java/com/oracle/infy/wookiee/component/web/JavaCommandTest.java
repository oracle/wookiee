package com.oracle.infy.wookiee.component.web;

import com.oracle.infy.wookiee.component.web.client.WookieeWebClient;
import com.oracle.infy.wookiee.component.web.util.EndpointTestHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import scala.collection.immutable.Map$;
import scala.concurrent.duration.Duration;

@DisplayName("Core Command Test")
public class JavaCommandTest extends EndpointTestHelper {
    @Test
    public void commandTest() {
        beforeAll();

        var response = WookieeWebClient.oneOff("http://localhost:" + externalPort(),
                "GET", "java", "payload",
                Map$.MODULE$.empty(), Duration.apply(10, "seconds"), ec());

        assert response.code() == 200;
    }

    @Override
    public void registerEndpoints(WebManager manager) {
        System.out.println("Reg endpoints1");
        WookieeEndpoints.registerEndpoint(JavaCommand::new, conf(), ec());
        System.out.println("Reg endpoints2");
    }
}
