// TEMPLATE 12 — A scheduled background job
// Where it goes: src/main/java/com/telemetrix/ingestion/NightlyImportJob.java
//
// Requires @EnableScheduling on the main class (template 01). Spring then calls @Scheduled
// methods on a timer, in a background thread — no HTTP request involved.

package com.telemetrix.library;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
// Only create this bean when the setting is on — so tests and local runs don't fire it by accident.
//   application.properties:  jobs.nightly-import.enabled=true
@ConditionalOnProperty(name = "jobs.nightly-import.enabled", havingValue = "true")
public class NightlyImportJob {

    private static final Logger log = LoggerFactory.getLogger(NightlyImportJob.class);

    private final OpenF1Client openF1;

    public NightlyImportJob(OpenF1Client openF1) {
        this.openF1 = openF1;
    }

    // cron = "second minute hour day-of-month month day-of-week"  →  every day at 03:00.
    // Put it in properties to change it without recompiling: cron = "${jobs.nightly-import.cron}".
    // Other forms: fixedDelay = 60_000 (60 s after the previous run ENDED),
    //              fixedRate  = 60_000 (every 60 s, regardless of how long a run takes).
    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void run() {
        // Catch and log: an exception here only kills this run, and would otherwise go unnoticed.
        try {
            int year = java.time.Year.now().getValue();
            var meetings = openF1.meetingsOrEmpty(year);
            log.info("Nightly import: {} meetings for {}", meetings.size(), year);
            // … hand them to a service that saves them (the job itself holds no logic) …
        } catch (Exception e) {
            log.error("Nightly import failed", e);
        }
    }
}
