package com.meterflow.shared;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The web apps get a JSON 401 (no browser popup); other clients keep the Basic challenge. */
class BasicEntryPointTest {

    @Test
    void browserAppsGetNoChallenge() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        var response = new MockHttpServletResponse();
        SecurityConfig.basicEntryPoint().commence(request, response, new BadCredentialsException("bad"));
        assertEquals(401, response.getStatus());
        assertNull(response.getHeader("WWW-Authenticate"));
        assertTrue(response.getContentAsString().contains("Invalid or missing credentials"));
    }

    @Test
    void otherClientsGetBasicChallenge() throws Exception {
        var response = new MockHttpServletResponse();
        SecurityConfig.basicEntryPoint().commence(new MockHttpServletRequest(), response, new BadCredentialsException("bad"));
        assertEquals(401, response.getStatus());
        assertTrue(response.getHeader("WWW-Authenticate").startsWith("Basic"));
    }
}
