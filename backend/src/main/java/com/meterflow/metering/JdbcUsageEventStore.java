package com.meterflow.metering;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Batched {@code INSERT ... ON CONFLICT DO NOTHING}. The unique idempotency-key constraint is the
 * single authority for duplicates, including concurrent requests, and one batch costs one
 * database round trip instead of two queries per event (QA2).
 *
 * <p>Do not enable the PostgreSQL driver option reWriteBatchedInserts: it hides per-row update
 * counts. If a driver does report SUCCESS_NO_INFO, the row is checked by id.</p>
 */
@Component
class JdbcUsageEventStore implements UsageEventStore {
    static final String INSERT = "INSERT INTO usage_events (id, customer_id, metric, quantity, timestamp, idempotency_key, properties_json) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING";

    private final JdbcTemplate jdbc;

    JdbcUsageEventStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public boolean[] insertIgnoringDuplicates(List<NewEvent> events) {
        if (events.isEmpty()) return new boolean[0];
        List<Object[]> args = new ArrayList<>(events.size());
        for (NewEvent e : events) {
            args.add(new Object[]{e.id(), e.customerId(), e.metric(), e.quantity(),
                    OffsetDateTime.ofInstant(e.timestamp(), ZoneOffset.UTC), e.idempotencyKey(), e.propertiesJson()});
        }
        int[] counts = jdbc.batchUpdate(INSERT, args);
        boolean[] inserted = new boolean[events.size()];
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] == Statement.SUCCESS_NO_INFO) {
                List<UUID> stored = jdbc.queryForList("SELECT id FROM usage_events WHERE idempotency_key = ?",
                        UUID.class, events.get(i).idempotencyKey());
                inserted[i] = stored.contains(events.get(i).id());
            } else {
                inserted[i] = counts[i] > 0;
            }
        }
        return inserted;
    }
}
