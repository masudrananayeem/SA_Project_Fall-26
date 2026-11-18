package com.meterflow.subscriptions;

import java.util.UUID;

/**
 * Published when a subscription is created. For a prepaid plan the invoicing module listens and
 * issues the up-front package invoice for the current month (the subscription waits in
 * PENDING_PAYMENT until it is paid).
 */
public record SubscriptionStarted(UUID subscriptionId, String customerId, UUID planId, boolean prepaid) { }
