package org.openldes.ldesadmin.application.service.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openldes.ldesadmin.application.port.in.instance.InstanceAlreadyRegisteredException;
import org.openldes.ldesadmin.application.port.in.instance.RegisterInstanceUseCase.RegisterInstanceCommand;
import org.openldes.ldesadmin.application.port.out.persistence.InstanceRepository;
import org.openldes.ldesadmin.application.service.TestFixtures;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceOrigin;
import org.openldes.ldesadmin.model.instance.InstanceType;

@ExtendWith(MockitoExtension.class)
class RegisterInstanceServiceTest {

  @Mock
  private InstanceRepository instanceRepository;

  @Test
  void registersAndStoresTheInstance() {
    RegisterInstanceService service = new RegisterInstanceService(instanceRepository,
        TestFixtures.CLOCK);

    Instance instance = service.register(new RegisterInstanceCommand(InstanceType.LDES_SERVER,
        "Production", URI.create("https://ldes.example.org/"), false));

    assertThat(instance.origin()).isEqualTo(InstanceOrigin.REGISTERED);
    assertThat(instance.url()).hasToString("https://ldes.example.org");
    assertThat(instance.registeredAt()).isEqualTo(TestFixtures.NOW);
    verify(instanceRepository).save(instance);
  }

  @Test
  void refusesToRegisterTheSameUrlTwice() {
    RegisterInstanceService service = new RegisterInstanceService(instanceRepository,
        TestFixtures.CLOCK);
    URI url = URI.create("https://ldes.example.org");
    when(instanceRepository.existsByTypeAndUrl(InstanceType.LDES_SERVER, url)).thenReturn(true);

    RegisterInstanceCommand command = new RegisterInstanceCommand(InstanceType.LDES_SERVER,
        "Production", URI.create("https://ldes.example.org/"), false);

    assertThatThrownBy(() -> service.register(command))
        .isInstanceOf(InstanceAlreadyRegisteredException.class);
    verify(instanceRepository, never()).save(any());
  }
}
