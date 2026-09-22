// TEMPLATE 10a — Reading your own settings from application.properties
// Where it goes: src/main/java/com/telemetrix/<feature>/OpenF1Properties.java
//
// Binds every key starting with "openf1." to this record:
//     openf1.base-url=https://api.openf1.org/v1    → baseUrl()
//     openf1.timeout=10s                           → timeout()   ("10s" becomes a Duration)
// Type-safe, and validated at startup: a missing base-url stops the app with a clear message
// instead of failing later at runtime.
//
// Registered by @ConfigurationPropertiesScan on the main class (template 01).
// (Alternative for a single value: @Value("${openf1.base-url}") String baseUrl in a constructor.
//  Fine for one key; a properties record is cleaner for a group.)

package com.telemetrix.library;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "openf1")
@Validated
public record OpenF1Properties(
        @NotBlank String baseUrl,               // kebab-case key base-url → camelCase baseUrl
        @DefaultValue("10s") Duration timeout) {}
