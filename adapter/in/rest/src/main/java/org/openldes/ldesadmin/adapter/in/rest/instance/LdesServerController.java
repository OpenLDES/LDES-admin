package org.openldes.ldesadmin.adapter.in.rest.instance;

import org.openldes.ldesadmin.model.instance.InstanceType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(LdesServerController.PATH)
class LdesServerController extends InstanceController {

  static final String PATH = "/api/v1/ldes-servers";

  LdesServerController(InstanceUseCases useCases) {
    super(InstanceType.LDES_SERVER, PATH, useCases);
  }
}
