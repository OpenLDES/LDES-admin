package org.openldes.ldesadmin.application.service.instance;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openldes.ldesadmin.application.port.in.instance.InstanceNotFoundException;
import org.openldes.ldesadmin.application.port.out.persistence.InstanceRepository;
import org.openldes.ldesadmin.application.port.out.runtime.LocalInstanceRuntime;
import org.openldes.ldesadmin.application.service.TestFixtures;
import org.openldes.ldesadmin.application.service.monitoring.ActivityRecorder;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceType;
import org.openldes.ldesadmin.model.monitoring.ActivityEvent.Severity;

@ExtendWith(MockitoExtension.class)
class UnregisterInstanceServiceTest {

  @Mock private InstanceRepository instanceRepository;
  @Mock private LocalInstanceRuntime localInstanceRuntime;
  @Mock private ActivityRecorder activity;

  private UnregisterInstanceService service;

  @BeforeEach
  void setUp() {
    service =
        new UnregisterInstanceService(
            new InstanceLookup(instanceRepository),
            instanceRepository,
            localInstanceRuntime,
            activity);
  }

  @Test
  void removesARegisteredInstanceWithoutTouchingTheRuntime() {
    Instance server = TestFixtures.ldesServer();
    when(instanceRepository.findById(server.id())).thenReturn(Optional.of(server));

    service.unregister(InstanceType.LDES_SERVER, server.id());

    verify(instanceRepository).deleteById(server.id());
    verifyNoInteractions(localInstanceRuntime);
    verify(activity).record(server, Severity.INFO, "LDES server removed", null);
  }

  @Test
  void stopsALocalInstanceBeforeRemovingIt() {
    Instance ldio = TestFixtures.localLdio();
    when(instanceRepository.findById(ldio.id())).thenReturn(Optional.of(ldio));

    service.unregister(InstanceType.LDIO, ldio.id());

    InOrder inOrder = inOrder(localInstanceRuntime, instanceRepository);
    inOrder.verify(localInstanceRuntime).stop("container-42");
    inOrder.verify(instanceRepository).deleteById(ldio.id());
    verify(activity).record(ldio, Severity.INFO, "Local LDIO stopped", null);
  }

  @Test
  void doesNotFindAnInstanceOfAnotherType() {
    Instance server = TestFixtures.ldesServer();
    when(instanceRepository.findById(server.id())).thenReturn(Optional.of(server));

    assertThatThrownBy(() -> service.unregister(InstanceType.LDIO, server.id()))
        .isInstanceOf(InstanceNotFoundException.class);
    verify(instanceRepository, never()).deleteById(any());
  }
}
