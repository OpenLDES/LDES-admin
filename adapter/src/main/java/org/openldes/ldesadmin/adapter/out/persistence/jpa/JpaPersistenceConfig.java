package org.openldes.ldesadmin.adapter.out.persistence.jpa;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * The Spring Boot application class lives in the bootstrap module, outside this package, so the
 * entities and repositories have to be registered explicitly.
 */
@Configuration(proxyBeanMethods = false)
@EntityScan(basePackageClasses = InstanceJpaEntity.class)
@EnableJpaRepositories(basePackageClasses = InstanceJpaRepository.class)
class JpaPersistenceConfig {

}
