package org.openldes.ldesadmin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest(properties = "ldes-admin.security.enabled=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecuredApiIT {

  @Autowired private MockMvcTester mvc;

  /** Replaces the decoder that would otherwise be built from the issuer URI. */
  @MockitoBean private JwtDecoder jwtDecoder;

  @Test
  void requiresATokenForTheApi() {
    assertThat(mvc.get().uri("/api/v1/ldes-servers")).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void acceptsAuthenticatedRequests() {
    assertThat(mvc.get().uri("/api/v1/ldes-servers").with(jwt())).hasStatusOk();
  }

  @Test
  void keepsHealthPublic() {
    assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();
  }

  @Test
  void keepsTheRestOfTheActuatorPrivate() {
    assertThat(mvc.get().uri("/actuator/metrics")).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void keepsTheFrontendPublic() {
    // It holds no data; the API it calls is what needs the token. Without a built frontend the
    // page is missing, but it is not refused.
    assertThat(mvc.get().uri("/sandbox").exchange().getResponse().getStatus())
        .isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
  }
}
