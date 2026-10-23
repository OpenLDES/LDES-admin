package org.openldes.ldesadmin.application.port.in.dashboard;

import org.openldes.ldesadmin.model.dashboard.DashboardOverview;

/**
 * Collects the health and activity of every managed LDES server and LDIO. Instances that cannot
 * be reached are reported as such, they do not make the whole overview fail.
 */
public interface GetDashboardUseCase {

  DashboardOverview overview();
}
