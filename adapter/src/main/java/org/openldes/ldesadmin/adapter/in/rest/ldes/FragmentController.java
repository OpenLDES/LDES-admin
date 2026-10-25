package org.openldes.ldesadmin.adapter.in.rest.ldes;

import java.util.List;
import java.util.UUID;
import org.openldes.ldesadmin.application.port.in.ldes.FragmentUseCase;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldes.Fragment;
import org.openldes.ldesadmin.model.ldes.Fragment.Relation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Browsing the pages of a view, through the fetch API of the server.
 */
@RestController
@RequestMapping("/api/v1/ldes-servers/{serverId}/eventstreams/{collection}/views/{view}")
class FragmentController {

  private final FragmentUseCase fragmentUseCase;

  FragmentController(FragmentUseCase fragmentUseCase) {
    this.fragmentUseCase = fragmentUseCase;
  }

  /**
   * @param url IRI of the page, as found in a relation of another page; the root of the view when
   *            absent
   */
  @GetMapping("/fragment")
  FragmentWebModel fragment(@PathVariable UUID serverId, @PathVariable String collection,
      @PathVariable String view, @RequestParam(required = false) String url) {
    Fragment fragment = fragmentUseCase.fetch(new InstanceId(serverId), collection, view, url);
    return new FragmentWebModel(fragment.url(), collection, view, url == null || url.isBlank(),
        fragment.immutable(), fragment.relations(),
        fragment.members().stream()
            .map(member -> new MemberWebModel(member.id(), member.isVersionOf(),
                member.timestamp(), member.content().content()))
            .toList());
  }

  /**
   * @param root whether this is the root page of the view
   */
  record FragmentWebModel(String url, String collection, String view, boolean root,
                          boolean immutable, List<Relation> relations,
                          List<MemberWebModel> members) {

  }

  /**
   * @param content the member in Turtle
   */
  record MemberWebModel(String id, String isVersionOf, String timestamp, String content) {

  }
}
