package com.oracle.infy.qa;

import com.oracle.infy.wookiee.service.WookieeService;
import com.oracle.infy.wookiee.service.meta.WookieeServiceMeta;
import com.oracle.infy.wookiee.test.BaseWookieeTest;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Test;
import scala.Option;
import scala.collection.immutable.HashMap;
import scala.collection.immutable.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class BasicServiceTest implements BaseWookieeTest {
  Map<String, Class<? extends WookieeService>> servicesMap = new HashMap<String, Class<BasicService>>().updated("base", BasicService.class);
  @Override
  public Option<Map<String, Class<? extends WookieeService>>> servicesMap() {
    return Option.apply(servicesMap);
  }

  @Test
  public void testBasicServiceStartsItselfUp() {
    Option<WookieeServiceMeta> testService = testWookiee().getService("base");
    assertNotNull("Basic Service was not registered", testService);
    assertEquals(testService.get().name(), "base");
  }
}
