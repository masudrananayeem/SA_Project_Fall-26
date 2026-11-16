package com.meterflow.metering;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Test double with the same contract as the database store: the idempotency key is unique and
 * duplicates (also inside one batch) are skipped. Keeps the stored events for assertions.
 */
public class InMemoryUsageEventStore implements UsageEventStore {
    private final Map<String, UsageEvent> byKey = new HashMap<>();
    private final Map<UUID, UsageEvent> byId = new HashMap<>();
    private final Map<String, List<UsageEvent>> byCustomer = new HashMap<>();

    @Override
    public synchronized boolean[] insertIgnoringDuplicates(List<NewEvent> events) {
        boolean[] inserted = new boolean[events.size()];
        for (int i = 0; i < events.size(); i++) {
            NewEvent e = events.get(i);
            if (byKey.containsKey(e.idempotencyKey())) continue;
            UsageEvent stored = toEntity(e);
            byKey.put(e.idempotencyKey(), stored);
            byId.put(e.id(), stored);
            byCustomer.computeIfAbsent(e.customerId(), k -> new ArrayList<>()).add(stored);
            inserted[i] = true;
        }
        return inserted;
    }

    public static UsageEvent toEntity(NewEvent e) {
        UsageEvent entity = new UsageEvent(e.customerId(), e.metric(), e.quantity(), e.timestamp(), e.idempotencyKey(), e.propertiesJson());
        try {
            Field id = UsageEvent.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(entity, e.id());
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return entity;
    }

    public int size() { return byId.size(); }

    public Collection<UsageEvent> all() { return byId.values(); }

    public UsageEvent byId(UUID id) { return byId.get(id); }

    public List<UsageEvent> window(String customer, String metric, java.time.Instant from, java.time.Instant to) {
        return byCustomer.getOrDefault(customer, List.of()).stream()
                .filter(e -> e.getMetric().equals(metric) && !e.getTimestamp().isBefore(from) && e.getTimestamp().isBefore(to))
                .sorted(Comparator.comparing(UsageEvent::getTimestamp).thenComparing(UsageEvent::getId))
                .toList();
    }
}
