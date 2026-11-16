package com.meterflow.metering;

import java.time.Instant;
import java.util.List;

/**
 * Published once per accepted ingestion batch. Stored in the Spring Modulith event publication
 * registry in the same transaction as the events (outbox), then delivered asynchronously to
 * listeners such as budget evaluation, so slow consumers never slow down ingestion.
 */
public record UsageRecorded(List<String> customerIds, List<String> periods, int acceptedEvents, Instant recordedAt) { }
