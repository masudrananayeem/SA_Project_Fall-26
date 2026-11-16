package com.meterflow.metering;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Write path for raw usage events. */
public interface UsageEventStore {

    record NewEvent(UUID id, String customerId, String metric, BigDecimal quantity, Instant timestamp,
                    String idempotencyKey, String propertiesJson) { }

    /**
     * Inserts the events in one round trip, skipping any whose idempotency key already exists
     * (including duplicates inside the same batch). Returns, per input position, whether that
     * event was newly stored.
     */
    boolean[] insertIgnoringDuplicates(List<NewEvent> events);
}
