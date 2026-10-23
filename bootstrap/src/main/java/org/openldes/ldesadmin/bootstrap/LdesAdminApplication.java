package org.openldes.ldesadmin.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Starts LDES-admin. This module only wires the hexagon together: the adapters are picked up by
 * component scanning, the application services by {@link ApplicationServiceConfig}.
 */
@SpringBootApplication(scanBasePackages = "org.openldes.ldesadmin")
public class LdesAdminApplication {

  public static void main(String[] args) {
    SpringApplication.run(LdesAdminApplication.class, args);
  }

}
