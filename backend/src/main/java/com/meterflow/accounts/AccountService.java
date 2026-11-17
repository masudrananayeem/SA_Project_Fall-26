package com.meterflow.accounts;

import com.meterflow.subscriptions.SubscriptionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Customer sign-up and admin-created customer logins. */
@Service
public class AccountService {
    static final Pattern USERNAME = Pattern.compile("^[a-z0-9][a-z0-9_-]{2,39}$");
    static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Set<String> RESERVED = Set.of("admin", "administrator", "root", "system", "support", "meterflow", "api");

    private final CustomerAccountRepository accounts;
    private final SubscriptionRepository subscriptions;
    private final PasswordEncoder encoder;
    private final String adminUser;
    private final String demoCustomerUser;

    public AccountService(CustomerAccountRepository accounts, SubscriptionRepository subscriptions, PasswordEncoder encoder,
                          @Value("${meterflow.security.admin-user:admin}") String adminUser,
                          @Value("${meterflow.security.customer-user:customer}") String demoCustomerUser) {
        this.accounts = accounts;
        this.subscriptions = subscriptions;
        this.encoder = encoder;
        this.adminUser = adminUser;
        this.demoCustomerUser = demoCustomerUser;
    }

    public record NewAccount(String username, String password, String displayName, String email, String company) { }

    /**
     * Self-service sign-up. The username becomes the customer ID, so it must not belong to an
     * existing customer (otherwise a stranger could claim that customer's usage and invoices).
     */
    @Transactional
    public CustomerAccount signUp(NewAccount input) {
        String username = normalise(input.username());
        if (!subscriptions.findByCustomerIdOrderByPeriodStartDesc(username).isEmpty())
            throw new IllegalArgumentException("This customer ID is already in use. Choose another username.");
        return create(username, input);
    }

    /** Admin creates a login, also for a customer ID that already has subscriptions or invoices. */
    @Transactional
    public CustomerAccount createByAdmin(NewAccount input) {
        return create(normalise(input.username()), input);
    }

    @Transactional(readOnly = true)
    public List<CustomerAccount> all() { return accounts.findAllByOrderByCreatedAtDesc(); }

    @Transactional(readOnly = true)
    public java.util.Optional<CustomerAccount> find(String username) { return accounts.findByUsername(username); }

    @Transactional
    public CustomerAccount setEnabled(String username, boolean enabled) {
        var account = accounts.findByUsername(username).orElseThrow(() -> new java.util.NoSuchElementException("Account not found"));
        account.setEnabled(enabled);
        return accounts.save(account);
    }

    @Transactional
    public CustomerAccount updateProfile(String username, String displayName, String email, String company) {
        var account = accounts.findByUsername(username).orElseThrow(() -> new java.util.NoSuchElementException("Account not found"));
        validateProfile(displayName, email);
        account.updateProfile(displayName.trim(), email.trim(), blankToNull(company));
        return accounts.save(account);
    }

    private CustomerAccount create(String username, NewAccount input) {
        validateUsername(username);
        if (input.password() == null || input.password().length() < 8)
            throw new IllegalArgumentException("Password must be at least 8 characters.");
        validateProfile(input.displayName(), input.email());
        if (accounts.existsByUsername(username)) throw new IllegalArgumentException("This username is already taken.");
        return accounts.save(new CustomerAccount(username, encoder.encode(input.password()), input.displayName().trim(),
                input.email().trim(), blankToNull(input.company())));
    }

    void validateUsername(String username) {
        if (!USERNAME.matcher(username).matches())
            throw new IllegalArgumentException("Username must be 3-40 characters: lowercase letters, digits, '-' or '_', starting with a letter or digit.");
        if (RESERVED.contains(username) || username.equalsIgnoreCase(adminUser) || username.equalsIgnoreCase(demoCustomerUser))
            throw new IllegalArgumentException("This username is reserved.");
    }

    private static void validateProfile(String displayName, String email) {
        if (displayName == null || displayName.isBlank() || displayName.trim().length() > 120)
            throw new IllegalArgumentException("Name is required (max 120 characters).");
        if (email == null || !EMAIL.matcher(email.trim()).matches() || email.trim().length() > 200)
            throw new IllegalArgumentException("Enter a valid email address.");
    }

    static String normalise(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
