package com.meterflow.notifications;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class WebhookController {
    private final WebhookRepository repo;
    private final WebhookDeliveryService delivery;
    private final CustomerNotificationRepository notifications;

    public WebhookController(WebhookRepository repo, WebhookDeliveryService delivery, CustomerNotificationRepository notifications) {
        this.repo = repo;
        this.delivery = delivery;
        this.notifications = notifications;
    }

    public record Input(@NotBlank String customerId, @NotBlank String endpoint, @NotBlank @Size(min = 16) String secret) { }

    @GetMapping("/webhooks") @PreAuthorize("hasRole('ADMIN')")
    public List<Webhook> all() { return repo.findAll(); }

    @PostMapping("/webhooks") @PreAuthorize("hasRole('ADMIN')")
    public Webhook create(@Valid @RequestBody Input i) {
        var uri = java.net.URI.create(i.endpoint());
        if (uri.getScheme() == null || uri.getHost() == null) throw new IllegalArgumentException("endpoint must be an absolute http(s) URL");
        return repo.save(new Webhook(i.customerId(), i.endpoint(), i.secret()));
    }

    /** Delivery log for one endpoint (status, attempts, last response, next attempt). */
    @GetMapping("/webhooks/{id}/deliveries") @PreAuthorize("hasRole('ADMIN')")
    public List<WebhookDelivery> deliveries(@PathVariable UUID id) { return delivery.all(id); }

    /** Manual replay of a delivered or failed webhook. */
    @PostMapping("/webhooks/deliveries/{id}/replay") @PreAuthorize("hasRole('ADMIN')")
    public WebhookDelivery replay(@PathVariable UUID id) { return delivery.replay(id); }

    /** In-app notifications for a customer (budget alerts, invoices, payment and dunning steps). */
    @GetMapping("/notifications")
    public List<CustomerNotification> notifications(@RequestParam String customerId, Authentication auth) {
        if (auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN")) && !auth.getName().equals(customerId))
            throw new AccessDeniedException("Customers may only read their own notifications");
        return notifications.findTop50ByCustomerIdOrderByCreatedAtDesc(customerId);
    }
}
