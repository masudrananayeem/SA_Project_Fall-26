package com.meterflow.accounts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Customer accounts:
 * - POST /api/auth/signup: public self-service sign-up (customer portal).
 * - GET/PUT /api/account: the signed-in customer's profile.
 * - /api/customers: admin view and admin-created logins.
 */
@RestController
public class AccountController {
    private final AccountService service;

    public AccountController(AccountService service) { this.service = service; }

    public record SignUpInput(@NotBlank @Size(max = 40) String username, @NotBlank @Size(min = 8, max = 100) String password,
                              @NotBlank @Size(max = 120) String displayName, @NotBlank @Size(max = 200) String email,
                              @Size(max = 160) String company) { }

    public record ProfileInput(@NotBlank @Size(max = 120) String displayName, @NotBlank @Size(max = 200) String email,
                               @Size(max = 160) String company) { }

    @PostMapping("/api/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> signUp(@Valid @RequestBody SignUpInput in) {
        var account = service.signUp(new AccountService.NewAccount(in.username(), in.password(), in.displayName(), in.email(), in.company()));
        return Map.of("username", account.getUsername(), "customerId", account.getCustomerId());
    }

    /** Profile of the signed-in customer (the demo customer has no stored profile). */
    @GetMapping("/api/account")
    public Map<String, Object> me(Authentication auth) {
        return service.find(auth.getName()).map(AccountController::view).orElseGet(() -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("username", auth.getName());
            m.put("customerId", auth.getName());
            m.put("displayName", auth.getName());
            m.put("email", null);
            m.put("company", null);
            m.put("enabled", true);
            m.put("createdAt", null);
            m.put("stored", false);
            return m;
        });
    }

    @PutMapping("/api/account")
    public Map<String, Object> update(@Valid @RequestBody ProfileInput in, Authentication auth) {
        if (service.find(auth.getName()).isEmpty())
            throw new IllegalArgumentException("The built-in demo account has no editable profile.");
        return view(service.updateProfile(auth.getName(), in.displayName(), in.email(), in.company()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/customers")
    public List<Map<String, Object>> all() { return service.all().stream().map(AccountController::view).toList(); }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/customers")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@Valid @RequestBody SignUpInput in) {
        return view(service.createByAdmin(new AccountService.NewAccount(in.username(), in.password(), in.displayName(), in.email(), in.company())));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/customers/{username}/enabled")
    public Map<String, Object> enable(@PathVariable String username, @RequestParam boolean value) {
        if (service.find(username).isEmpty()) throw new NoSuchElementException("Account not found");
        return view(service.setEnabled(username, value));
    }

    private static Map<String, Object> view(CustomerAccount a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("username", a.getUsername());
        m.put("customerId", a.getCustomerId());
        m.put("displayName", a.getDisplayName());
        m.put("email", a.getEmail());
        m.put("company", a.getCompany());
        m.put("enabled", a.isEnabled());
        m.put("createdAt", a.getCreatedAt());
        m.put("stored", true);
        return m;
    }
}
