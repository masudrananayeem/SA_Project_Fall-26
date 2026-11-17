package com.meterflow.accounts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Local/cloud login store (HTTP Basic): the configured admin and demo customer, plus every
 * customer account created by sign-up or by an admin. The prod profile uses OIDC JWTs instead.
 */
@Component
@Profile("!prod")
public class AccountUserDetailsService implements UserDetailsService {
    private final CustomerAccountRepository accounts;
    private final UserDetails admin;
    private final UserDetails demoCustomer;

    public AccountUserDetailsService(CustomerAccountRepository accounts, PasswordEncoder encoder,
                                     @Value("${meterflow.security.admin-user}") String adminUser,
                                     @Value("${meterflow.security.admin-password}") String adminPassword,
                                     @Value("${meterflow.security.customer-user}") String customerUser,
                                     @Value("${meterflow.security.customer-password}") String customerPassword) {
        this.accounts = accounts;
        this.admin = User.withUsername(adminUser).password(encoder.encode(adminPassword)).roles("ADMIN").build();
        this.demoCustomer = User.withUsername(customerUser).password(encoder.encode(customerPassword)).roles("CUSTOMER").build();
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null) throw new UsernameNotFoundException("Unknown user");
        if (username.equals(admin.getUsername())) return copy(admin);
        if (username.equals(demoCustomer.getUsername())) return copy(demoCustomer);
        var account = accounts.findByUsername(username.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        return User.withUsername(account.getUsername())
                .password(account.getPasswordHash())
                .roles("CUSTOMER")
                .disabled(!account.isEnabled())
                .build();
    }

    /** Spring Security erases credentials after login, so never hand out the cached instance. */
    private static UserDetails copy(UserDetails u) {
        return User.withUserDetails(u).build();
    }
}
