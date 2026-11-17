package com.meterflow.shared;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.web.cors.*;
import java.util.stream.Collectors;

@Configuration @EnableMethodSecurity
public class SecurityConfig {
    /**
     * BCrypt cost is configurable: local Basic Auth re-hashes on every request, so load tests (QA2)
     * use a low cost (for example 4) or a JWT in the prod profile. Default 10.
     */
    @Bean PasswordEncoder passwordEncoder(@Value("${meterflow.security.password-hash-strength:10}") int strength) {
        return new BCryptPasswordEncoder(Math.max(4, Math.min(31, strength)));
    }

    // The local/cloud user store (configured admin + demo customer + sign-up accounts) is
    // accounts.AccountUserDetailsService.

    @Bean @Profile("!prod") SecurityFilterChain localFilter(HttpSecurity http) throws Exception {
        return http.cors(Customizer.withDefaults()).csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(a -> a.requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/auth/signup").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(b -> b.authenticationEntryPoint(basicEntryPoint())).build();
    }

    /**
     * Browser apps (admin console, customer portal) send X-Requested-With: XMLHttpRequest. For them a
     * failed login is a plain JSON 401 without a WWW-Authenticate challenge, so the browser does not
     * open its native sign-in popup and the app can show its own error. Other clients (curl, Swagger,
     * smoke test) keep the standard Basic challenge.
     */
    static AuthenticationEntryPoint basicEntryPoint() {
        var basic = new BasicAuthenticationEntryPoint();
        basic.setRealmName("MeterFlow");
        return (request, response, ex) -> {
            if ("XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))) {
                response.setStatus(401);
                response.setContentType("application/problem+json");
                response.getWriter().write("{\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"Invalid or missing credentials\"}");
            } else {
                basic.commence(request, response, ex);
            }
        };
    }

    @Bean @Profile("prod") SecurityFilterChain productionFilter(HttpSecurity http) throws Exception {
        return http.cors(Customizer.withDefaults()).csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(a -> a.requestMatchers("/actuator/health/**").permitAll().anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(roleConverter()))).build();
    }

    private Converter<Jwt, ? extends AbstractAuthenticationToken> roleConverter() {
        return jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null) roles = List.of();
            var authorities = roles.stream().map(r -> r.startsWith("ROLE_") ? r : "ROLE_"+r)
                    .map(SimpleGrantedAuthority::new).collect(Collectors.toList());
            return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        };
    }

    @Bean CorsConfigurationSource corsConfigurationSource(
            @Value("${meterflow.cors.allowed-origins:http://localhost:5173,http://localhost:5174}") String allowedOrigins) {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(x -> !x.isBlank()).toList());
        c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization","Content-Type","X-Correlation-ID","X-Requested-With"));
        c.setExposedHeaders(List.of("X-Correlation-ID")); c.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource(); s.registerCorsConfiguration("/**", c); return s;
    }
}
