package org.openldes.ldesadmin.adapter.out.runtime;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Docker images used for local LDES servers and LDIOs.
 *
 * @param startupTimeout how long to wait for a local instance to accept requests; the first start
 *                       includes pulling the images
 */
@ConfigurationProperties("ldes-admin.local-runtime")
public record LocalRuntimeProperties(
    @DefaultValue("openldes/ldes-server:4.2.1") String ldesServerImage,
    @DefaultValue("openldes/ldi-orchestrator:3.1.1") String ldioImage,
    @DefaultValue("postgres:16-alpine") String postgresImage,
    @DefaultValue("PT5M") Duration startupTimeout) {

}
