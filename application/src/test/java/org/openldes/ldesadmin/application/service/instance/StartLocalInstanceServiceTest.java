package org.openldes.ldesadmin.application.service.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openldes.ldesadmin.application.port.in.instance.StartLocalInstanceUseCase.StartLocalInstanceCommand;
import org.openldes.ldesadmin.application.port.out.persistence.InstanceRepository;
import org.openldes.ldesadmin.application.port.out.runtime.LocalInstanceRuntime;
import org.openldes.ldesadmin.application.port.out.runtime.LocalInstanceRuntime.LocalRuntimeHandle;
import org.openldes.ldesadmin.application.service.TestFixtures;
import org.openldes.ldesadmin.application.service.monitoring.ActivityRecorder;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceType;
import org.openldes.ldesadmin.model.monitoring.ActivityEvent.Severity;

@ExtendWith(MockitoExtension.class)
class StartLocalInstanceServiceTest {

  @Mock private LocalInstanceRuntime localInstanceRuntime;
  @Mock private InstanceRepository instanceRepository;
  @Mock private ActivityRecorder activity;

  private StartLocalInstanceService service;

  @BeforeEach
  void setUp() {
    service =
        new StartLocalInstanceService(
            localInstanceRuntime, instanceRepository, activity, TestFixtures.CLOCK);
  }

  @Test
  void startsAndRegistersALocalInstance() {
    when(localInstanceRuntime.start(InstanceType.LDES_SERVER, "sandbox"))
        .thenReturn(
            new LocalRuntimeHandle(
                URI.create("http://localhost:32770"),
                URI.create("http://sandbox:8080"),
                "container-7"));

    Instance instance =
        service.start(new StartLocalInstanceCommand(InstanceType.LDES_SERVER, "sandbox", true));

    assertThat(instance.isLocal()).isTrue();
    assertThat(instance.sandbox()).isTrue();
    assertThat(instance.url()).hasToString("http://localhost:32770");
    assertThat(instance.internalUrl()).hasToString("http://sandbox:8080");
    assertThat(instance.runtimeReference()).isEqualTo("container-7");
    verify(instanceRepository).save(instance);
    verify(activity)
        .record(
            eq(instance),
            eq(Severity.SUCCESS),
            eq("Local LDES server started"),
            contains("http://sandbox:8080"));
  }

  @Test
  void stopsTheInstanceAgainWhenItCannotBeRegistered() {
    when(localInstanceRuntime.start(InstanceType.LDIO, "sandbox"))
        .thenReturn(
            new LocalRuntimeHandle(
                URI.create("http://localhost:32771"),
                URI.create("http://sandbox:8080"),
                "container-8"));
    doThrow(new IllegalStateException("database down")).when(instanceRepository).save(any());

    StartLocalInstanceCommand command =
        new StartLocalInstanceCommand(InstanceType.LDIO, "sandbox", false);

    assertThatThrownBy(() -> service.start(command)).hasMessage("database down");
    verify(localInstanceRuntime).stop("container-8");
  }

  @Test
  void requiresAName() {
    StartLocalInstanceCommand command =
        new StartLocalInstanceCommand(InstanceType.LDIO, " ", false);

    assertThatThrownBy(() -> service.start(command)).isInstanceOf(IllegalArgumentException.class);
  }
}
