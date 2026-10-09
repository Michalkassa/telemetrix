package com.telemetrix.ingestion.openf1;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

// Settings under "openf1." in application.properties (all optional; these are the defaults).
@ConfigurationProperties(prefix = "openf1")
@Validated
public record OpenF1Properties(
		@DefaultValue("https://api.openf1.org/v1") @NotBlank String baseUrl,
		@DefaultValue("10s") Duration timeout,
		// attempts per request, including the first; 429 and 5xx responses are retried
		@DefaultValue("4") @Min(1) int maxAttempts,
		// wait before the 1st retry, doubled for each further one (unless OpenF1 sends Retry-After)
		@DefaultValue("1s") Duration initialBackoff,
		// free tier: 3 requests/s and 30 requests/min, so at most one request every 2 s
		@DefaultValue("2s") Duration minRequestInterval) {
}
