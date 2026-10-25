package org.openldes.ldesadmin.bootstrap;

import java.io.IOException;
import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the built frontend (the frontend module, packaged in {@code static/}) on {@code /}.
 *
 * <p>The frontend routes in the browser, so a page such as {@code /ldes-servers/{id}} has no file:
 * every path that is not a file, the API or the actuator gets {@code index.html}. The assets have
 * a content hash in their name, so browsers may keep them; {@code index.html} is checked on every
 * visit, so a new version shows right away.
 */
@Configuration(proxyBeanMethods = false)
class FrontendConfig implements WebMvcConfigurer {

  private static final String LOCATION = "classpath:/static/";
  private static final String INDEX = "index.html";

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/assets/**")
        .addResourceLocations(LOCATION + "assets/")
        .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    registry.addResourceHandler("/**")
        .addResourceLocations(LOCATION)
        .setCacheControl(CacheControl.noCache())
        .resourceChain(true)
        .addResolver(new ClientRouteResolver());
  }

  /**
   * Whether a path is a route of the frontend rather than a file, or an endpoint of the backend
   * that does not exist.
   */
  static boolean isClientRoute(String path) {
    String lastSegment = path.substring(path.lastIndexOf('/') + 1);
    return !path.startsWith("api/") && !path.startsWith("actuator/") && !lastSegment.contains(".");
  }

  private static final class ClientRouteResolver extends PathResourceResolver {

    @Override
    protected Resource getResource(String resourcePath, Resource location) throws IOException {
      Resource resource = super.getResource(resourcePath, location);
      if (resource != null || !isClientRoute(resourcePath)) {
        return resource;
      }
      return super.getResource(INDEX, location);
    }
  }
}
