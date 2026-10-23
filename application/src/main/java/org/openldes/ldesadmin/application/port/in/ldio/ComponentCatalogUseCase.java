package org.openldes.ldesadmin.application.port.in.ldio;

import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldio.ComponentCatalog;

/**
 * The inputs, adapters, transformers and outputs that can be used in pipelines on an LDIO.
 *
 * @see PipelineUseCase for the exceptions thrown
 */
public interface ComponentCatalogUseCase {

  ComponentCatalog catalog(InstanceId ldioId);
}
