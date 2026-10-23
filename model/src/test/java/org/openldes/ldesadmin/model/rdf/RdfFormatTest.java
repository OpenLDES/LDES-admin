package org.openldes.ldesadmin.model.rdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class RdfFormatTest {

  @Test
  void findsFormatIgnoringParametersAndCase() {
    assertThat(RdfFormat.fromMediaType("text/turtle;charset=UTF-8")).isEqualTo(RdfFormat.TURTLE);
    assertThat(RdfFormat.fromMediaType("Application/N-Quads")).isEqualTo(RdfFormat.N_QUADS);
  }

  @Test
  void rejectsNonRdfMediaTypes() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> RdfFormat.fromMediaType("application/json"))
        .withMessageContaining("application/json");
  }
}
