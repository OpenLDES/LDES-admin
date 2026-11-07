package org.openldes.ldesadmin.adapter.out.runtime;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How local LDES servers and LDIOs are run.
 *
 * @param startupTimeout how long to wait for a local instance to accept requests; the first start
 *     includes pulling the images
 * @param fragmentationCron how often a local LDES server fragments new members; more often than the
 *     server default, so experiments show results quickly
 */
@ConfigurationProperties("ldes-admin.local-runtime")
public record LocalRuntimeProperties(
    @DefaultValue("openldes/ldes-server:4.2.1") String ldesServerImage,
    @DefaultValue("openldes/ldi-orchestrator:3.1.1") String ldioImage,
    @DefaultValue("postgres:16-alpine") String postgresImage,
    @DefaultValue("PT5M") Duration startupTimeout,
    @DefaultValue("*/5 * * * * *") String fragmentationCron) {}
