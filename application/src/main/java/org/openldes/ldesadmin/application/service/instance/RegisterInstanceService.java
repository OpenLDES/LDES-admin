package org.openldes.ldesadmin.application.service.instance;

import java.time.Clock;
import org.openldes.ldesadmin.application.port.in.instance.InstanceAlreadyRegisteredException;
import org.openldes.ldesadmin.application.port.in.instance.RegisterInstanceUseCase;
import org.openldes.ldesadmin.application.port.out.persistence.InstanceRepository;
import org.openldes.ldesadmin.application.service.ApplicationService;
import org.openldes.ldesadmin.application.service.monitoring.ActivityRecorder;
import org.openldes.ldesadmin.model.instance.Instance;

@ApplicationService
public class RegisterInstanceService implements RegisterInstanceUseCase {

  private final InstanceRepository instanceRepository;
  private final ActivityRecorder activity;
  private final Clock clock;

  public RegisterInstanceService(InstanceRepository instanceRepository, ActivityRecorder activity,
      Clock clock) {
    this.instanceRepository = instanceRepository;
    this.activity = activity;
    this.clock = clock;
  }

  @Override
  public Instance register(RegisterInstanceCommand command) {
    Instance instance = Instance.register(command.type(), command.name(), command.url(),
        command.sandbox(), clock.instant());
    if (instanceRepository.existsByTypeAndUrl(instance.type(), instance.url())) {
      throw new InstanceAlreadyRegisteredException(instance.type(), instance.url());
    }
    instanceRepository.save(instance);
    activity.success(instance, instance.type().label() + " added");
    return instance;
  }
}
