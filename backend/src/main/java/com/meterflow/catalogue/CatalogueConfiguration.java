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
            if (plans.count() == 0) {
                plans.save(new Plan("starter", 1, "Starter API Plan", "BDT", new BigDecimal("299.00"), new BigDecimal("0.05"),
                        "api_calls", new BigDecimal("1000"), new BigDecimal("299.00")));
                plans.save(new Plan("growth", 1, "Growth API Plan", "BDT", new BigDecimal("999.00"), new BigDecimal("0.025"),
                        "api_calls", new BigDecimal("10000"), new BigDecimal("999.00")));
            }
            // V23: a prepaid package (pay before use), also added to databases seeded by earlier versions.
            if (plans.findTopByPlanKeyOrderByVersionDesc("pro-prepaid").isEmpty()) {
                var pro = new Plan("pro-prepaid", 1, "Pro Prepaid Package", "BDT", new BigDecimal("1500.00"), BigDecimal.ZERO,
                        "api_calls", new BigDecimal("100000"), BigDecimal.ZERO);
                pro.setTaxRate(new BigDecimal("15"));
                pro.setBillingMode(Plan.PREPAID);
                plans.save(pro);
            }
        };
    }
}
