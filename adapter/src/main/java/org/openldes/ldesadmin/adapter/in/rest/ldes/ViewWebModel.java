package org.openldes.ldesadmin.adapter.in.rest.ldes;

import java.util.List;
import java.util.Map;
import org.openldes.ldesadmin.model.ldes.View;

/**
 * @param definition the view in Turtle, as returned by the LDES server
 */
public record ViewWebModel(
    String collection,
    String name,
    int pageSize,
    List<TypedConfigWebModel> fragmentations,
    List<TypedConfigWebModel> retentionPolicies,
    String definition) {

  public static ViewWebModel fromDomain(View view) {
    return new ViewWebModel(view.collection(), view.name(), view.pageSize(),
        view.fragmentations().stream()
            .map(fragmentation -> new TypedConfigWebModel(fragmentation.type(),
                fragmentation.properties()))
            .toList(),
        view.retentionPolicies().stream()
            .map(policy -> new TypedConfigWebModel(policy.type(), policy.properties()))
            .toList(),
        view.definition().content());
  }

  /**
   * A fragmentation or retention policy.
   */
  public record TypedConfigWebModel(String type, Map<String, String> properties) {

  }
}
