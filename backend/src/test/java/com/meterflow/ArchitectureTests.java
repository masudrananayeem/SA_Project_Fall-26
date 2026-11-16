package com.meterflow;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** Spring Modulith verification: no dependency cycles and no access to another module's internals. */
class ArchitectureTests {
    @Test
    void moduleBoundariesAreValid() {
        var modules = ApplicationModules.of(MeterFlowApplication.class);
        assertDoesNotThrow(modules::verify);
    }
}
