package org.openldes.ldesadmin.adapter.out.runtime;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LocalRuntimeProperties.class)
class LocalRuntimeConfig {}
