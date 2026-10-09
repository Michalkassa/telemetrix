package com.telemetrix.ingestion.jolpica;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

// Settings under "jolpica." in application.properties (all optional; these are the defaults).
@ConfigurationProperties(prefix = "jolpica")
@Validated
public record JolpicaProperties(
		@DefaultValue("https://api.jolpi.ca/ergast/f1") @NotBlank String baseUrl,
		@DefaultValue("10s") Duration timeout,
		@DefaultValue("4") @Min(1) int maxAttempts,
		@DefaultValue("2s") Duration initialBackoff,
		// unauthenticated: 4 requests/s burst, 500 requests/hour sustained -> one every 7.2 s
		@DefaultValue("8s") Duration minRequestInterval,
		// the free database dumps: one download for the whole history (14 days delayed)
		@DefaultValue("https://api.jolpi.ca/data/dumps/download/") @NotBlank String dumpsUrl) {
}
