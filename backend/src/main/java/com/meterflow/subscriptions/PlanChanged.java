package com.meterflow.subscriptions;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Published after a plan change is recorded (IMMEDIATE, SCHEDULED or MIGRATION). The invoicing
 * module settles the price difference at once when the change falls in a period that is already
 * invoiced (a prepaid package that was billed up front).
 */
public record PlanChanged(UUID subscriptionId, String customerId, UUID planChangeId, UUID fromPlanId, UUID toPlanId,
                          LocalDate effectiveDate, String kind) { }
