package com.meterflow.accounts;

import com.meterflow.subscriptions.Subscription;
import com.meterflow.subscriptions.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountServiceTest {
    private final CustomerAccountRepository accounts = mock(CustomerAccountRepository.class);
    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final AccountService service = new AccountService(accounts, subscriptions, encoder, "admin", "customer");

    private AccountService.NewAccount input(String username) {
        return new AccountService.NewAccount(username, "s3cret-pass", "Acme Ltd", "billing@acme.test", "Acme");
    }

    @Test
    void signUpStoresHashedPasswordAndLowercaseUsername() {
        when(subscriptions.findByCustomerIdOrderByPeriodStartDesc("acme")).thenReturn(List.of());
        when(accounts.save(any(CustomerAccount.class))).thenAnswer(i -> i.getArgument(0));
        var account = service.signUp(input("  Acme "));
        assertEquals("acme", account.getUsername());
        assertEquals("acme", account.getCustomerId());
        assertTrue(encoder.matches("s3cret-pass", account.getPasswordHash()));
        assertNotEquals("s3cret-pass", account.getPasswordHash());
    }

    @Test
    void signUpCannotClaimAnExistingCustomerId() {
        when(subscriptions.findByCustomerIdOrderByPeriodStartDesc("acme"))
                .thenReturn(List.of(new Subscription("acme", UUID.randomUUID(), LocalDate.now(), LocalDate.now().plusYears(1))));
        assertThrows(IllegalArgumentException.class, () -> service.signUp(input("acme")));
        verify(accounts, never()).save(any());
    }

    @Test
    void adminCanCreateLoginForExistingCustomer() {
        when(accounts.save(any(CustomerAccount.class))).thenAnswer(i -> i.getArgument(0));
        assertEquals("acme", service.createByAdmin(input("acme")).getUsername());
    }

    @Test
    void reservedAndInvalidUsernamesAreRejected() {
        when(subscriptions.findByCustomerIdOrderByPeriodStartDesc(any())).thenReturn(List.of());
        for (String bad : List.of("admin", "customer", "root", "ab", "has space", "-start", "x".repeat(41))) {
            assertThrows(IllegalArgumentException.class, () -> service.signUp(input(bad)), bad);
        }
    }

    @Test
    void duplicateUsernameAndWeakPasswordAreRejected() {
        when(subscriptions.findByCustomerIdOrderByPeriodStartDesc(any())).thenReturn(List.of());
        when(accounts.existsByUsername("taken")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.signUp(input("taken")));
        assertThrows(IllegalArgumentException.class,
                () -> service.signUp(new AccountService.NewAccount("fresh", "short", "Name", "a@b.co", null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.signUp(new AccountService.NewAccount("fresh", "long-enough", "Name", "not-an-email", null)));
    }
}
