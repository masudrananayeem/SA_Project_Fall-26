package com.meterflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@Modulithic(systemName = "MeterFlow Usage Billing")
@EnableScheduling
public class MeterFlowApplication {
  public static void main(String[] args) { SpringApplication.run(MeterFlowApplication.class, args); }
}
