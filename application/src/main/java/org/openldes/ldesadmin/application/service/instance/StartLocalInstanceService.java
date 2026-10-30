package org.openldes.ldesadmin.application.service.instance;

import java.time.Clock;
import org.openldes.ldesadmin.application.port.in.instance.StartLocalInstanceUseCase;
import org.openldes.ldesadmin.application.port.out.persistence.InstanceRepository;
import org.openldes.ldesadmin.application.port.out.runtime.LocalInstanceRuntime;
import org.openldes.ldesadmin.application.port.out.runtime.LocalInstanceRuntime.LocalRuntimeHandle;
import org.openldes.ldesadmin.application.service.ApplicationService;
import org.openldes.ldesadmin.application.service.monitoring.ActivityRecorder;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.monitoring.ActivityEvent.Severity;

@ApplicationService
public class StartLocalInstanceService implements StartLocalInstanceUseCase {

  private final LocalInstanceRuntime localInstanceRuntime;
  private final InstanceRepository instanceRepository;
  private final ActivityRecorder activity;
  private final Clock clock;

  public StartLocalInstanceService(
      LocalInstanceRuntime localInstanceRuntime,
      InstanceRepository instanceRepository,
      ActivityRecorder activity,
      Clock clock) {
    this.localInstanceRuntime = localInstanceRuntime;
    this.instanceRepository = instanceRepository;
    this.activity = activity;
    this.clock = clock;
  }

  @Override
  public Instance start(StartLocalInstanceCommand command) {
    if (command.name() == null || command.name().isBlank()) {
      throw new IllegalArgumentException("'name' must not be blank");
    }
    LocalRuntimeHandle handle = localInstanceRuntime.start(command.type(), command.name());
    try {
      Instance instance =
          Instance.local(
              command.type(),
              command.name(),
              handle.url(),
              handle.internalUrl(),
              handle.runtimeReference(),
              command.sandbox(),
              clock.instant());
      instanceRepository.save(instance);
      activity.record(
          instance,
          Severity.SUCCESS,
          "Local " + command.type().label() + " started",
          "On "
              + instance.url()
              + ", and on "
              + instance.internalUrl()
              + " for other local instances");
      return instance;
    } catch (RuntimeException e) {
      // Don't leave an instance running that LDES-admin doesn't know about
      localInstanceRuntime.stop(handle.runtimeReference());
      throw e;
    }
  }
}
