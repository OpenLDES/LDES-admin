package org.openldes.ldesadmin.bootstrap;

import java.time.Clock;
import org.openldes.ldesadmin.application.service.ApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * Turns the framework-free services of the application ring into Spring beans.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(
    basePackageClasses = ApplicationService.class,
    useDefaultFilters = false,
    includeFilters = @Filter(type = FilterType.ANNOTATION, classes = ApplicationService.class))
class ApplicationServiceConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
