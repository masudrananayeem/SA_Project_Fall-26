package com.meterflow;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Spring Modulith verification: no dependency cycles and no access to another module's internals. */
class ArchitectureTests {
    @Test
    void moduleBoundariesAreValid() {
        ApplicationModules.of(MeterFlowApplication.class).verify();
    }
}