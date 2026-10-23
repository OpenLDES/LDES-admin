package org.openldes.ldesadmin.adapter.in.rest.dashboard;

import org.openldes.ldesadmin.application.port.in.dashboard.GetDashboardUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
class DashboardController {

  private final GetDashboardUseCase getDashboardUseCase;

  DashboardController(GetDashboardUseCase getDashboardUseCase) {
    this.getDashboardUseCase = getDashboardUseCase;
  }

  @GetMapping
  DashboardWebModel overview() {
    return DashboardWebModel.fromDomain(getDashboardUseCase.overview());
  }
}
