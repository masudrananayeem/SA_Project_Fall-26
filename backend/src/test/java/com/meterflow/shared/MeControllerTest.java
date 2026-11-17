package com.meterflow.shared;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MeControllerTest {
    private final MeController controller = new MeController();

    @Test
    void adminHasNoCustomerScope() {
        var auth = new UsernamePasswordAuthenticationToken("admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        var me = controller.me(auth);
        assertEquals("admin", me.get("username"));
        assertEquals(List.of("ADMIN"), me.get("roles"));
        assertNull(me.get("customerId"));
    }

    @Test
    void customerIsScopedToOwnId() {
        var auth = new UsernamePasswordAuthenticationToken("acme", null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        var me = controller.me(auth);
        assertEquals(List.of("CUSTOMER"), me.get("roles"));
        assertEquals("acme", me.get("customerId"));
    }
}
