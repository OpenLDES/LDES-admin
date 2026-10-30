package org.openldes.ldesadmin.adapter.out.http;

import org.openldes.ldesadmin.model.instance.Instance;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Creates {@link RestClient RestClients} pointing at an instance. They share the timeouts and other
 * HTTP client settings of the application ({@code spring.http.clients.*}).
 */
@Component
public class InstanceRestClients {

  private final RestClient.Builder restClientBuilder;

  public InstanceRestClients(RestClient.Builder restClientBuilder) {
    this.restClientBuilder = restClientBuilder;
  }

  public RestClient forInstance(Instance instance) {
    return restClientBuilder.clone().baseUrl(instance.url().toString()).build();
  }
}
