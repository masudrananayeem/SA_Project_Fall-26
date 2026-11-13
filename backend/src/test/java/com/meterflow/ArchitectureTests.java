package com.meterflow;
import org.junit.jupiter.api.Test;import org.springframework.modulith.core.ApplicationModules;import static org.junit.jupiter.api.Assertions.*;
class ArchitectureTests {@Test void moduleBoundariesAreValid(){var modules=ApplicationModules.of(MeterFlowApplication.class);assertDoesNotThrow(modules::verify);}}
