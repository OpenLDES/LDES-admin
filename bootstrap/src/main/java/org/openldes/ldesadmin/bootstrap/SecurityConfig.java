package org.openldes.ldesadmin.bootstrap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Secures the API as an OAuth2 resource server when {@code ldes-admin.security.enabled} is set;
 * configure the issuer with {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}.
 * Without it (the default, for local use) the API is open.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

  private static final String ENABLED = "ldes-admin.security.enabled";

  @Bean
  @ConditionalOnBooleanProperty(ENABLED)
  SecurityFilterChain securedApi(HttpSecurity http) throws Exception {
    return http
        .authorizeHttpRequests(requests -> requests
            .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
            .anyRequest().authenticated())
        .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(AbstractHttpConfigurer::disable)
        .build();
  }

  @Bean
  @ConditionalOnBooleanProperty(name = ENABLED, havingValue = false, matchIfMissing = true)
  SecurityFilterChain openApi(HttpSecurity http) throws Exception {
    return http
        .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
        .csrf(AbstractHttpConfigurer::disable)
        .build();
  }
}
