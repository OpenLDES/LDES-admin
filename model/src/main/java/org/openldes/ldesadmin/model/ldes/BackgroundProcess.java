package org.openldes.ldesadmin.model.ldes;

import java.util.Objects;

/**
 * A step of a background job of an LDES server, e.g. step {@code pagination} of job
 * {@code fragmentation}, with what its batch metrics tell since the server started.
 *
 * @param completedRuns  number of runs that completed
 * @param failedRuns     number of runs that failed
 * @param averageMillis  average duration of a completed run, 0 when there was none
 * @param longestMillis  duration of the longest recent completed run
 * @param running        whether the step is running right now
 */
public record BackgroundProcess(String job, String step, long completedRuns, long failedRuns,
                                long averageMillis, long longestMillis, boolean running) {

  public BackgroundProcess {
    Objects.requireNonNull(job, "'job' must not be null");
    Objects.requireNonNull(step, "'step' must not be null");
  }
}
