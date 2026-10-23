package org.openldes.ldesadmin;

import org.springframework.boot.SpringApplication;

public class TestLdesAdminApplication {

  public static void main(String[] args) {
    SpringApplication.from(LdesAdminApplication::main).with(TestcontainersConfiguration.class)
        .run(args);
  }

}
