package com.meterflow.credits;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Prepaid credit balances and credit notes (FR6). Balances are locked while being changed. */
@Service
public class CreditService {
    private final CreditAccountRepository accounts;
    private final CreditNoteRepository notes;

    public CreditService(CreditAccountRepository accounts, CreditNoteRepository notes) {
        this.accounts = accounts;
        this.notes = notes;
    }

    @Transactional
    public CreditAccount topUp(String customer, String currency, BigDecimal amount) {
        var account = accounts.lockByCustomer(customer).orElseGet(() -> accounts.save(new CreditAccount(customer, currency)));
        if (!account.getCurrency().equals(currency)) throw new IllegalArgumentException("Credit currency must match account currency");
        account.add(amount);
        return accounts.save(account);
    }

    /** Credit note for a dispute: recorded and added to the prepaid balance, used on the next invoice. */
    @Transactional
    public CreditNote issueNote(String customer, String currency, BigDecimal amount, String reason) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Credit note amount must be positive");
        var account = accounts.lockByCustomer(customer).orElseGet(() -> accounts.save(new CreditAccount(customer, currency)));
        if (!account.getCurrency().equals(currency)) throw new IllegalArgumentException("Credit note currency mismatch");
        account.add(amount);
        accounts.save(account);
        return notes.save(new CreditNote(customer, amount, currency, reason));
    }

    /**
     * Consumes up to {@code amount} of prepaid credit before charging. A customer without an account,
     * or with credit in another currency, simply has nothing to apply.
     */
    @Transactional
    public BigDecimal apply(String customer, String currency, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return BigDecimal.ZERO;
        var account = accounts.lockByCustomer(customer).orElse(null);
        if (account == null || !account.getCurrency().equals(currency)) return BigDecimal.ZERO;
        BigDecimal used = account.consume(amount);
        accounts.save(account);
        return used;
    }

    public CreditAccount balance(String customer) {
        return accounts.findByCustomerId(customer).orElseGet(() -> new CreditAccount(customer, "BDT"));
    }

    public List<CreditNote> notes(String customer) { return notes.findByCustomerIdOrderByCreatedAtDesc(customer); }
}
