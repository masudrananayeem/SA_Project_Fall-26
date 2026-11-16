package com.meterflow.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * FR9: HMAC-SHA256 signed webhooks with retry/backoff (30s x 2^n, 5 attempts), a delivery log per
 * attempt and manual replay. Receivers verify {@code X-MeterFlow-Signature: sha256=<hex>} over
 * {@code <timestamp>.<body>} using the header X-MeterFlow-Timestamp.
 */
@Service
public class WebhookDeliveryService {
    private final WebhookRepository hooks;
    private final WebhookDeliveryRepository deliveries;
    private final ObjectMapper mapper;
    private final Set<String> allowedHosts = new HashSet<>();
    private final boolean production;
    private final boolean allowPrivateHosts;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public WebhookDeliveryService(WebhookRepository hooks, WebhookDeliveryRepository deliveries, ObjectMapper mapper,
                                  @Value("${meterflow.webhooks.allowed-hosts:}") String hosts,
                                  @Value("${meterflow.webhooks.allow-private-hosts:false}") boolean allowPrivateHosts,
                                  Environment env) {
        this.hooks = hooks;
        this.deliveries = deliveries;
        this.mapper = mapper;
        for (String host : hosts.split(",")) if (!host.isBlank()) allowedHosts.add(host.trim().toLowerCase(Locale.ROOT));
        this.production = Arrays.asList(env.getActiveProfiles()).contains("prod");
        this.allowPrivateHosts = allowPrivateHosts && !production;
    }

    /** Queues one delivery per active endpoint of the customer (called inside the event listener's transaction). */
    @Transactional
    public List<WebhookDelivery> publish(String customer, String type, Map<String, Object> data) {
        String payload;
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("id", UUID.randomUUID().toString());
            envelope.put("type", type);
            envelope.put("createdAt", Instant.now().toString());
            envelope.put("data", data);
            payload = mapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("Could not create webhook delivery", e);
        }
        List<WebhookDelivery> created = new ArrayList<>();
        for (var h : hooks.findByCustomerIdAndActiveTrue(customer)) created.add(deliveries.save(new WebhookDelivery(h.getId(), type, payload)));
        return created;
    }

    @Scheduled(fixedDelayString = "${meterflow.webhooks.dispatch-ms:15000}")
    public void dispatchDue() {
        for (var d : deliveries.findByStatusAndNextAttemptAtBefore("PENDING", Instant.now())) dispatch(d.getId());
    }

    /** One delivery attempt; the outcome is always saved (the attempt log is the delivery row). */
    public void dispatch(UUID id) {
        var d = deliveries.findById(id).orElseThrow();
        if (!"PENDING".equals(d.getStatus()) || (d.getNextAttemptAt() != null && d.getNextAttemptAt().isAfter(Instant.now()))) return;
        var h = hooks.findById(d.getWebhookId()).orElse(null);
        if (h == null) {
            d.failure("Webhook endpoint removed");
            deliveries.save(d);
            return;
        }
        try {
            URI uri = URI.create(h.getEndpoint());
            if (!safe(uri)) {
                d.failure("Rejected unsafe or invalid endpoint");
            } else {
                String timestamp = String.valueOf(Instant.now().getEpochSecond());
                var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .header("X-MeterFlow-Event", d.getEventType())
                        .header("X-MeterFlow-Delivery", d.getId().toString())
                        .header("X-MeterFlow-Timestamp", timestamp)
                        .header("X-MeterFlow-Signature", "sha256=" + sign(timestamp + "." + d.getPayload(), h.getSecret()))
                        .POST(HttpRequest.BodyPublishers.ofString(d.getPayload())).build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) d.success("HTTP " + response.statusCode());
                else d.failure("HTTP " + response.statusCode() + ": " + response.body().substring(0, Math.min(700, response.body().length())));
            }
        } catch (Exception ex) {
            d.failure(ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
        deliveries.save(d);
    }

    boolean safe(URI uri) {
        String host = uri.getHost();
        if (host == null || uri.getUserInfo() != null || uri.getFragment() != null) return false;
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !(scheme.equals("http") && !production)) return false;
        if (production && !allowedHosts.contains(host.toLowerCase(Locale.ROOT))) return false;
        if (allowPrivateHosts) return true;
        if (host.matches("[0-9.]+") || host.contains(":")) return false;
        return isPublicHost(host);
    }

    private boolean isPublicHost(String host) {
        try {
            for (var address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String sign(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    public List<WebhookDelivery> all(UUID hook) { return deliveries.findByWebhookIdOrderByCreatedAtDesc(hook); }

    @Transactional
    public WebhookDelivery replay(UUID id) {
        var d = deliveries.findById(id).orElseThrow();
        d.replay();
        return deliveries.save(d);
    }
}
