package org.openldes.ldesadmin.bootstrap;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Runs the scheduled inbound adapters, e.g. the instance monitor (adapter/in/scheduling).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig {

}
