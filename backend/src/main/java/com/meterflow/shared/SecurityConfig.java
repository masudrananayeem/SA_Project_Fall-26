package com.meterflow.shared;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder(){ return new BCryptPasswordEncoder(); }
 @Bean UserDetailsService users(@Value("${meterflow.security.admin-user}") String au,@Value("${meterflow.security.admin-password}") String ap,
 @Value("${meterflow.security.customer-user}") String cu,@Value("${meterflow.security.customer-password}") String cp,PasswordEncoder e){
   return new InMemoryUserDetailsManager(User.withUsername(au).password(e.encode(ap)).roles("ADMIN").build(),User.withUsername(cu).password(e.encode(cp)).roles("CUSTOMER").build());
 }
 @Bean SecurityFilterChain filter(HttpSecurity http)throws Exception{return http.csrf(c->c.disable()).authorizeHttpRequests(a->a.requestMatchers("/actuator/health/**","/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html").permitAll().anyRequest().authenticated()).httpBasic(Customizer.withDefaults()).build();}
}
