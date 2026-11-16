package com.meterflow.notifications;

import com.meterflow.budgets.BudgetThresholdReached;
import com.meterflow.invoicing.InvoiceFinalised;
import com.meterflow.payments.PaymentEvents;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Observer pattern: the notifications module subscribes to domain events of the other modules.
 * Events are delivered from the Spring Modulith event publication registry after the publishing
 * transaction commits, and re-delivered if a listener fails, so no notification is lost.
 * Each event becomes an in-app notification and one HMAC-signed webhook delivery per endpoint.
 */
@Component
public class BillingEventNotifier {
    private final WebhookDeliveryService webhooks;
    private final CustomerNotificationRepository notifications;

    public BillingEventNotifier(WebhookDeliveryService webhooks, CustomerNotificationRepository notifications) {
        this.webhooks = webhooks;
        this.notifications = notifications;
    }

    private void notify(String customerId, String type, String message, Map<String, Object> data) {
        notifications.save(new CustomerNotification(customerId, type, message));
        webhooks.publish(customerId, type, data);
    }

    @ApplicationModuleListener
    void on(InvoiceFinalised e) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("invoiceId", e.invoiceId().toString());
        data.put("period", e.period());
        data.put("total", e.total());
        data.put("currency", e.currency());
        notify(e.customerId(), "invoice.finalised", "Invoice for " + e.period() + " finalised: " + e.total().toPlainString() + " " + e.currency(), data);
    }

    @ApplicationModuleListener
    void on(PaymentEvents.PaymentFailed e) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("invoiceId", e.invoiceId().toString());
        data.put("amount", e.amount());
        data.put("currency", e.currency());
        data.put("attempt", e.attempt());
        data.put("reason", e.reason());
        data.put("nextAttemptAt", e.nextAttemptAt());
        notify(e.customerId(), "payment.failed", "Payment failed (attempt " + e.attempt() + "): " + e.reason() + ". Next attempt: " + e.nextAttemptAt(), data);
    }

    @ApplicationModuleListener
    void on(PaymentEvents.PaymentSucceeded e) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("invoiceId", e.invoiceId().toString());
        data.put("amount", e.amount());
        data.put("currency", e.currency());
        data.put("reference", e.reference());
        notify(e.customerId(), "payment.succeeded", "Payment received: " + e.amount().toPlainString() + " " + e.currency(), data);
    }

    @ApplicationModuleListener
    void on(PaymentEvents.DunningStatusChanged e) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("invoiceId", e.invoiceId().toString());
        data.put("status", e.status());
        data.put("attempt", e.attempt());
        data.put("nextAttemptAt", e.nextAttemptAt());
        String message = switch (e.status()) {
            case "RETRYING" -> "Payment overdue; we will retry on " + e.nextAttemptAt();
            case "PAST_DUE" -> "Account is past due; service will be suspended if not paid by " + e.nextAttemptAt();
            case "SUSPENDED" -> "Account suspended for non-payment. Pay the open invoice to reactivate immediately.";
            case "REACTIVATED" -> "Payment received; account reactivated.";
            default -> "Payment status: " + e.status();
        };
        notify(e.customerId(), "dunning." + e.status().toLowerCase(), message, data);
    }

    @ApplicationModuleListener
    void on(BudgetThresholdReached e) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("customerId", e.customerId());
        data.put("period", e.period());
        data.put("threshold", e.thresholdPercent());
        data.put("estimatedAmount", e.estimatedAmount());
        data.put("budget", e.budget());
        data.put("currency", e.currency());
        notify(e.customerId(), "usage.threshold_reached", "Spend reached " + e.thresholdPercent() + "% of your " + e.period()
                + " budget (" + e.estimatedAmount().toPlainString() + " of " + e.budget().toPlainString() + " " + e.currency() + ")", data);
    }
}
