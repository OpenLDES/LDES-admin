package org.openldes.ldesadmin.model.ldes;

import java.util.List;
import java.util.Objects;
import org.openldes.ldesadmin.model.rdf.RdfDocument;

/**
 * A page of a view, as served by the fetch API of an LDES server: its relations to other pages and
 * the members on it.
 *
 * @param url the IRI of the fragment, as published by the server
 * @param immutable whether the server marked the fragment as immutable (it will not change)
 */
public record Fragment(
    String url, boolean immutable, List<Relation> relations, List<Member> members) {

  public Fragment {
    Objects.requireNonNull(url, "'url' must not be null");
    relations = relations == null ? List.of() : List.copyOf(relations);
    members = members == null ? List.of() : List.copyOf(members);
  }

  /**
   * A {@code tree:Relation} to another fragment.
   *
   * @param type local name of the relation type, e.g. {@code GreaterThanOrEqualToRelation}
   * @param path the property path the relation applies to, may be {@code null}
   * @param value the value the relation compares with, may be {@code null}
   * @param node IRI of the fragment the relation points to
   * @param remainingItems number of members behind the relation, may be {@code null}
   */
  public record Relation(String type, String path, String value, String node, Long remainingItems) {

    public Relation {
      Objects.requireNonNull(type, "'type' must not be null");
      Objects.requireNonNull(node, "'node' must not be null");
    }
  }

  /**
   * A member on the fragment.
   *
   * @param isVersionOf the state object it is a version of, may be {@code null}
   * @param timestamp its timestamp, may be {@code null}
   * @param content the triples describing the member
   */
  public record Member(String id, String isVersionOf, String timestamp, RdfDocument content) {

    public Member {
      Objects.requireNonNull(id, "'id' must not be null");
      Objects.requireNonNull(content, "'content' must not be null");
    }
  }
}
