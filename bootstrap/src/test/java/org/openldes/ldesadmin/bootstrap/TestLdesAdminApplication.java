package org.openldes.ldesadmin.bootstrap;

import org.springframework.boot.SpringApplication;

/**
 * Runs LDES-admin locally with a Postgres container: {@code ./mvnw -pl bootstrap -am
 * spring-boot:test-run}, or run this class from the IDE.
 */
public class TestLdesAdminApplication {

  public static void main(String[] args) {
    SpringApplication.from(LdesAdminApplication::main).with(TestcontainersConfiguration.class)
        .run(args);
  }

}
