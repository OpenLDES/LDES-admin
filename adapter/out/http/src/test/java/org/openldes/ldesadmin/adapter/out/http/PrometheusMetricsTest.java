package org.openldes.ldesadmin.adapter.out.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.openldes.ldesadmin.adapter.out.http.PrometheusMetrics.Sample;

class PrometheusMetricsTest {

  @Test
  void parsesSamplesWithLabels() {
    PrometheusMetrics metrics =
        PrometheusMetrics.parse(
            """
        # HELP ldio_data_in_total
        # TYPE ldio_data_in_total counter
        ldio_data_in_total{ldio_type="Ldio:HttpIn",pipeline_name="parcels"} 1.5E3
        process_uptime_seconds 42.0
        """);

    assertThat(metrics.samples("ldio_data_in_total"))
        .singleElement()
        .satisfies(
            sample -> {
              assertThat(sample.label("pipeline_name")).isEqualTo("parcels");
              assertThat(sample.label("ldio_type")).isEqualTo("Ldio:HttpIn");
              assertThat(sample.longValue()).isEqualTo(1500);
            });
    assertThat(metrics.samples("process_uptime_seconds"))
        .extracting(Sample::value)
        .containsExactly(42.0);
  }

  @Test
  void handlesEscapedQuotesCommasAndTimestamps() {
    PrometheusMetrics metrics =
        PrometheusMetrics.parse(
            "http_requests_total{uri=\"/a,b\",message=\"say \\\"hi\\\"\"} 3 1729680000000");

    assertThat(metrics.samples("http_requests_total"))
        .singleElement()
        .satisfies(
            sample -> {
              assertThat(sample.label("uri")).isEqualTo("/a,b");
              assertThat(sample.label("message")).isEqualTo("say \"hi\"");
              assertThat(sample.value()).isEqualTo(3.0);
            });
  }

  @Test
  void treatsInfiniteValuesAsZeroCounts() {
    PrometheusMetrics metrics = PrometheusMetrics.parse("bucket{le=\"+Inf\"} +Inf");

    assertThat(metrics.samples("bucket"))
        .singleElement()
        .satisfies(sample -> assertThat(sample.longValue()).isZero());
  }
}
