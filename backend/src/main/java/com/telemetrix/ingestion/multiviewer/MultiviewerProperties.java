package com.telemetrix.ingestion.multiviewer;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

// Settings under "multiviewer." (all optional). No published limits: be polite, and every map is
// fetched only once.
@ConfigurationProperties(prefix = "multiviewer")
@Validated
public record MultiviewerProperties(
		@DefaultValue("https://api.multiviewer.app/api/v1") @NotBlank String baseUrl,
		@DefaultValue("10s") Duration timeout,
		@DefaultValue("3") @Min(1) int maxAttempts,
		@DefaultValue("2s") Duration initialBackoff,
		@DefaultValue("1s") Duration minRequestInterval) {
}
