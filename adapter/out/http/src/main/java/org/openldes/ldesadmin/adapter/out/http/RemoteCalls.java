package org.openldes.ldesadmin.adapter.out.http;

import java.util.function.Supplier;
import org.openldes.ldesadmin.application.port.out.RemoteInstanceException;
import org.openldes.ldesadmin.application.port.out.RemoteInstanceException.Reason;
import org.openldes.ldesadmin.model.instance.Instance;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Translates the errors of HTTP calls to an instance into {@link RemoteInstanceException
 * RemoteInstanceExceptions}, so they don't leak Spring types into the application ring.
 */
public final class RemoteCalls {

  private static final int MAX_BODY_LENGTH = 500;

  private RemoteCalls() {}

  /**
   * @param action what the call does, e.g. {@code "create event stream"}; used in error messages
   */
  public static <T> T call(Instance instance, String action, Supplier<T> call) {
    try {
      return call.get();
    } catch (HttpClientErrorException.NotFound e) {
      throw new RemoteInstanceException(Reason.NOT_FOUND, describe(instance, action, e), e);
    } catch (HttpClientErrorException e) {
      throw new RemoteInstanceException(Reason.REJECTED, describe(instance, action, e), e);
    } catch (RestClientResponseException e) {
      throw new RemoteInstanceException(Reason.UNAVAILABLE, describe(instance, action, e), e);
    } catch (ResourceAccessException e) {
      throw new RemoteInstanceException(
          Reason.UNAVAILABLE,
          "%s could not %s: %s is unreachable".formatted(label(instance), action, instance.url()),
          e);
    } catch (RestClientException e) {
      throw new RemoteInstanceException(
          Reason.UNAVAILABLE,
          "%s could not %s: %s".formatted(label(instance), action, e.getMessage()),
          e);
    }
  }

  public static void run(Instance instance, String action, Runnable call) {
    call(
        instance,
        action,
        () -> {
          call.run();
          return null;
        });
  }

  private static String describe(Instance instance, String action, RestClientResponseException e) {
    String body = e.getResponseBodyAsString().strip();
    if (body.length() > MAX_BODY_LENGTH) {
      body = body.substring(0, MAX_BODY_LENGTH) + "…";
    }
    return "%s could not %s: %s%s"
        .formatted(label(instance), action, e.getStatusCode(), body.isEmpty() ? "" : " - " + body);
  }

  private static String label(Instance instance) {
    return "%s '%s'".formatted(instance.type().label(), instance.name());
  }
}
