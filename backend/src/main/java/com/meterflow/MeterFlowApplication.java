package com.meterflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Modular monolith. Modules (direct sub-packages): catalogue, subscriptions, metering, budgets,
 * credits, invoicing, payments, notifications and shared. Modules talk through public services or
 * domain events stored in the event publication registry; ArchitectureTests verifies the rules.
 */
@SpringBootApplication
@Modulithic(systemName = "MeterFlow Usage Billing", sharedModules = "shared")
@EnableScheduling
@EnableAsync
public class MeterFlowApplication {
    public static void main(String[] args) { SpringApplication.run(MeterFlowApplication.class, args); }
}
