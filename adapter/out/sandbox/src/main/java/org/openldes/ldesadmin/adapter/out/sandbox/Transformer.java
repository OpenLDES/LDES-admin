package org.openldes.ldesadmin.adapter.out.sandbox;

import java.util.List;
import org.apache.jena.rdf.model.Model;

/** Captures the models passed on by a configured LDIO transformer. */
@FunctionalInterface
interface Transformer {
  List<Model> transform(Model model);
}
