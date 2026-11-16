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
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
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

    @Bean @Profile("!prod") UserDetailsService users(
            @Value("${meterflow.security.admin-user}") String adminUser,
            @Value("${meterflow.security.admin-password}") String adminPassword,
            @Value("${meterflow.security.customer-user}") String customerUser,
            @Value("${meterflow.security.customer-password}") String customerPassword, PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername(adminUser).password(encoder.encode(adminPassword)).roles("ADMIN").build(),
                User.withUsername(customerUser).password(encoder.encode(customerPassword)).roles("CUSTOMER").build());
    }

    @Bean @Profile("!prod") SecurityFilterChain localFilter(HttpSecurity http) throws Exception {
        return http.cors(Customizer.withDefaults()).csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(a -> a.requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll().anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults()).build();
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
            @Value("${meterflow.cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(x -> !x.isBlank()).toList());
        c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization","Content-Type","X-Correlation-ID"));
        c.setExposedHeaders(List.of("X-Correlation-ID")); c.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource(); s.registerCorsConfiguration("/**", c); return s;
    }
}
