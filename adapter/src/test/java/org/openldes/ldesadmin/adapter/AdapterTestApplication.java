package org.openldes.ldesadmin.adapter;

import java.time.Clock;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Configuration for the slice tests of the adapters; the real application lives in the bootstrap
 * module.
 */
@SpringBootApplication
class AdapterTestApplication {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
