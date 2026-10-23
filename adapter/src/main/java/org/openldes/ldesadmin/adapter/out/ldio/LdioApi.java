package org.openldes.ldesadmin.adapter.out.ldio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * The JSON documents of the LDIO pipeline management API ({@code /admin/api/v1}).
 */
final class LdioApi {

  private LdioApi() {
  }

  /**
   * A deployed pipeline, as listed by {@code GET /admin/api/v1/pipeline}.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record PipelineTO(String name, String description, String status, String updateSource,
                    InputComponentTO input, List<ComponentTO> transformers,
                    List<ComponentTO> outputs) {

  }

  /**
   * The body of {@code POST /admin/api/v1/pipeline}.
   */
  record PipelineConfigTO(String name, String description, InputComponentTO input,
                          List<ComponentTO> transformers, List<ComponentTO> outputs) {

  }

  /**
   * LDIO flattens nested config into dotted keys, e.g. {@code rdf-writer.content-type}.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record InputComponentTO(String name, Map<String, Object> config, ComponentTO adapter) {

  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ComponentTO(String name, Map<String, Object> config) {

  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record CatalogTO(List<String> inputs, List<String> adapters, List<String> transformers,
                   List<String> outputs) {

  }
}
