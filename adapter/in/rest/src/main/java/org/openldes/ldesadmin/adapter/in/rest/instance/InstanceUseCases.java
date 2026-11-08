package org.openldes.ldesadmin.adapter.in.rest.instance;

import org.openldes.ldesadmin.application.port.in.instance.CheckInstanceHealthUseCase;
import org.openldes.ldesadmin.application.port.in.instance.FindInstancesUseCase;
import org.openldes.ldesadmin.application.port.in.instance.RegisterInstanceUseCase;
import org.openldes.ldesadmin.application.port.in.instance.StartLocalInstanceUseCase;
import org.openldes.ldesadmin.application.port.in.instance.UnregisterInstanceUseCase;
import org.springframework.stereotype.Component;

/** The use cases shared by the LDES server and LDIO controllers. */
@Component
record InstanceUseCases(
    FindInstancesUseCase find,
    RegisterInstanceUseCase register,
    StartLocalInstanceUseCase startLocal,
    UnregisterInstanceUseCase unregister,
    CheckInstanceHealthUseCase checkHealth) {}
