package dev.ledgerflow.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Stateless OAuth2 resource server. Reads need {@code SCOPE_ledger.read}, writes need {@code SCOPE_ledger.write}.
 * <p>
 * In production the decoder comes from Boot's auto-configuration via
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}. For local runs a symmetric dev key can be
 * configured instead, see {@link LedgerflowProperties.Security}.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    static final String READ = "SCOPE_ledger.read";
    static final String WRITE = "SCOPE_ledger.write";

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/**").hasAuthority(READ)
                        .requestMatchers(HttpMethod.POST, "/api/**").hasAuthority(WRITE)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> { }))
                .build();
    }

    @Bean
    @ConditionalOnProperty("ledgerflow.security.dev-jwt-secret")
    JwtDecoder devJwtDecoder(LedgerflowProperties properties) {
        byte[] secret = properties.security().devJwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("ledgerflow.security.dev-jwt-secret must be at least 32 bytes");
        }
        return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}
