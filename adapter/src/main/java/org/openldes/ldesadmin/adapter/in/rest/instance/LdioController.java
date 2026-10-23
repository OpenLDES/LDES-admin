package org.openldes.ldesadmin.adapter.in.rest.instance;

import org.openldes.ldesadmin.model.instance.InstanceType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(LdioController.PATH)
class LdioController extends InstanceController {

  static final String PATH = "/api/v1/ldios";

  LdioController(InstanceUseCases useCases) {
    super(InstanceType.LDIO, PATH, useCases);
  }
}
