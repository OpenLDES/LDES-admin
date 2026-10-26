package org.openldes.ldesadmin.adapter.out.sandbox;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.openldes.ldesadmin.application.port.out.sandbox.ComponentException;
import org.openldes.ldi.requestexecutor.executor.RequestExecutor;
import org.openldes.ldi.requestexecutor.executor.ratelimiter.RateLimiterConfig;
import org.openldes.ldi.requestexecutor.executor.retry.RetryConfig;
import org.openldes.ldi.requestexecutor.services.RequestExecutorDecorator;
import org.openldes.ldi.requestexecutor.services.RequestExecutorFactory;
import org.openldes.ldi.requestexecutor.valueobjects.AuthStrategy;

/**
 * The HTTP client of a component, configured as LDIO's {@code LdioRequestExecutorSupplier} does
 * it: its headers, authentication (API key or OAuth2 client credentials), retries and rate limit.
 */
final class RequestExecutors {

  private static final String DEFAULT_API_KEY_HEADER = "X-API-KEY";

  private RequestExecutors() {
  }

  static RequestExecutor create(LdioConfig config) {
    return RequestExecutorDecorator.decorate(base(config))
        .with(retry(config))
        .with(rateLimiter(config))
        .get();
  }

  private static RequestExecutor base(LdioConfig config) {
    String type = config.optional("auth.type").orElse(AuthStrategy.NO_AUTH.name());
    AuthStrategy authentication = AuthStrategy.from(type).orElseThrow(() ->
        new ComponentException("%s cannot authenticate with %s: use one of %s".formatted(
            config.component(), type, Arrays.toString(AuthStrategy.values()))));
    List<Header> headers = headers(config);
    var factory = new RequestExecutorFactory();
    return switch (authentication) {
      case NO_AUTH -> factory.createNoAuthExecutor(headers);
      case API_KEY -> {
        List<Header> withApiKey = new ArrayList<>(headers);
        withApiKey.add(new BasicHeader(
            config.optional("auth.api-key-header").orElse(DEFAULT_API_KEY_HEADER),
            config.property("auth.api-key")));
        yield factory.createNoAuthExecutor(withApiKey);
      }
      case OAUTH2_CLIENT_CREDENTIALS -> factory.createClientCredentialsExecutor(headers,
          config.property("auth.client-id"), config.property("auth.client-secret"),
          config.property("auth.token-endpoint"), config.optional("auth.scope").orElse(null));
    };
  }

  private static List<Header> headers(LdioConfig config) {
    List<Header> headers = new ArrayList<>();
    for (int index = 0; ; index++) {
      Optional<String> key = config.optional("http.headers.%d.key".formatted(index));
      if (key.isEmpty()) {
        return headers;
      }
      headers.add(new BasicHeader(key.get(),
          config.property("http.headers.%d.value".formatted(index))));
    }
  }

  private static Retry retry(LdioConfig config) {
    if (!config.optionalBoolean("retries.enabled").orElse(true)) {
      return null;
    }
    List<Integer> statuses = config.optional("retries.statuses-to-retry")
        .map(csv -> Arrays.stream(csv.split(",")).map(String::strip).map(Integer::parseInt)
            .toList())
        .orElse(List.of());
    return RetryConfig.of(config.optionalInteger("retries.max").orElse(5), statuses).getRetry();
  }

  private static RateLimiter rateLimiter(LdioConfig config) {
    if (!config.optionalBoolean("rate-limit.enabled").orElse(false)) {
      return null;
    }
    return config.optionalInteger("rate-limit.max-requests-per-minute")
        .map(perMinute -> RateLimiterConfig.limitPerMinute(perMinute).getRateLimiter())
        .orElseGet(() -> RateLimiterConfig.limitForPeriod(
                config.optionalInteger("rate-limit.limit").orElse(500),
                config.optional("rate-limit.period").orElse("PT1M"))
            .getRateLimiter());
  }
}
