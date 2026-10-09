package com.telemetrix.ingestion;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Turns on @Scheduled jobs.
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig {

}
