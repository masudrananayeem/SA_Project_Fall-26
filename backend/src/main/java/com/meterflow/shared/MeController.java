package com.meterflow.shared;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Who is signed in. The admin console and the customer portal call this right after login to
 * check the credentials and to decide which interface the user may open. For a customer the
 * principal name is the customer ID used by every customer-scoped endpoint.
 */
@RestController
public class MeController {

    @GetMapping("/api/me")
    public Map<String, Object> me(Authentication auth) {
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .sorted()
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", auth.getName());
        body.put("roles", roles);
        body.put("customerId", roles.contains("ADMIN") ? null : auth.getName());
        return body;
    }
}
