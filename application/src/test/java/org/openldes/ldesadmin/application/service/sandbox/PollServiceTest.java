package org.openldes.ldesadmin.application.service.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openldes.ldesadmin.application.port.out.persistence.InstanceRepository;
import org.openldes.ldesadmin.application.port.out.sandbox.HttpPoller;
import org.openldes.ldesadmin.application.service.TestFixtures;
import org.openldes.ldesadmin.model.instance.InstanceType;
import org.openldes.ldesadmin.model.sandbox.Poll;
import org.openldes.ldesadmin.model.sandbox.PolledContent;

@ExtendWith(MockitoExtension.class)
class PollServiceTest {

  private static final PolledContent CONTENT = new PolledContent("application/json", "[]");

  @Mock
  private InstanceRepository instanceRepository;
  @Mock
  private HttpPoller httpPoller;

  private PollService service;

  @BeforeEach
  void setUp() {
    service = new PollService(instanceRepository, httpPoller);
    when(instanceRepository.findByType(InstanceType.LDES_SERVER))
        .thenReturn(List.of(TestFixtures.ldesServer(), TestFixtures.localLdesServer()));
    when(httpPoller.poll(any())).thenReturn(CONTENT);
  }

  @Test
  void pollsAUrlAsItIs() {
    when(instanceRepository.findByType(InstanceType.LDIO))
        .thenReturn(List.of(TestFixtures.localLdio()));
    Poll poll = new Poll(URI.create("https://api.example.org/changes?since=1"),
        Map.of("X-API-KEY", "secret"), null);

    assertThat(service.poll(poll)).isEqualTo(CONTENT);

    verify(httpPoller).poll(poll);
  }

  @Test
  void reachesLocalInstancesOnTheUrlOfLdesAdmin() {
    Poll poll = new Poll(URI.create("http://sandbox-ldes-server:8080/sandbox-parcels/by-page"),
        Map.of("Accept", "text/turtle"), null);

    service.poll(poll);

    verify(httpPoller).poll(
        poll.withUrl(URI.create("http://localhost:32769/sandbox-parcels/by-page")));
  }
}
