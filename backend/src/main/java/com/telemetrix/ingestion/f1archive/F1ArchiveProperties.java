package com.telemetrix.ingestion.f1archive;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

// Settings under "f1archive." (all optional). The archive is unofficial and only gives exact track
// status, so it can be switched off: race-control messages then give the same periods to the second.
// No published limits: be polite (about 3 requests per race, plus one index per year).
@ConfigurationProperties(prefix = "f1archive")
@Validated
public record F1ArchiveProperties(
		@DefaultValue("true") boolean enabled,
		@DefaultValue("https://livetiming.formula1.com/static") @NotBlank String baseUrl,
		@DefaultValue("10s") Duration timeout,
		@DefaultValue("3") @Min(1) int maxAttempts,
		@DefaultValue("2s") Duration initialBackoff,
		@DefaultValue("1s") Duration minRequestInterval) {
}
