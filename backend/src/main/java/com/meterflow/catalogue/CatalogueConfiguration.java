package com.meterflow.catalogue;

import com.meterflow.catalogue.charges.ChargeStrategy;
import com.meterflow.catalogue.charges.ChargeStrategyRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
class CatalogueConfiguration {

    /** Built-in charge types plus every ChargeStrategy bean in the context (QA4 extension point). */
    @Bean
    ChargeStrategyRegistry chargeStrategyRegistry(ObjectProvider<ChargeStrategy> extensions) {
        return ChargeStrategyRegistry.builtIn().with(extensions.orderedStream().toList());
    }

    /** Demo catalogue for an empty database. */
    @Bean
    CommandLineRunner seedCatalogue(PlanRepository plans) {
        return args -> {
            if (plans.count() > 0) return;
            plans.save(new Plan("starter", 1, "Starter API Plan", "BDT", new BigDecimal("299.00"), new BigDecimal("0.05"),
                    "api_calls", new BigDecimal("1000"), new BigDecimal("299.00")));
            plans.save(new Plan("growth", 1, "Growth API Plan", "BDT", new BigDecimal("999.00"), new BigDecimal("0.025"),
                    "api_calls", new BigDecimal("10000"), new BigDecimal("999.00")));
        };
    }
}
