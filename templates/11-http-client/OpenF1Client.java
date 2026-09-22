// TEMPLATE 11 — Calling an external REST API (e.g. OpenF1) with RestClient
// Where it goes: src/main/java/com/telemetrix/ingestion/OpenF1Client.java
//
// Needs dependency:
//     <dependency>
//         <groupId>org.springframework.boot</groupId>
//         <artifactId>spring-boot-starter-restclient</artifactId>
//     </dependency>
//
// Pattern: one small client class per external API. It hides URLs and JSON shapes, and returns
// plain Java records. The rest of the app never sees HTTP details — and tests can fake this class.

package com.telemetrix.library;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component // generic bean ("not a service/repository/controller, but manage it anyway")
public class OpenF1Client {

    private final RestClient http;

    public OpenF1Client(RestClient openF1RestClient) { // the bean from template 10b
        this.http = openF1RestClient;
    }

    // The JSON shape of the external API, as a record. It is NOT your entity — the outside world's
    // format and your database format are kept apart, and you map between them.
    // OpenF1 uses snake_case (meeting_key); @JsonProperty maps it to Java names.
    // @JsonIgnoreProperties(ignoreUnknown = true): extra fields they add later won't break you.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meeting(
            @JsonProperty("meeting_key") int meetingKey,
            @JsonProperty("meeting_name") String meetingName,
            @JsonProperty("country_name") String countryName,
            @JsonProperty("date_start") OffsetDateTime dateStart,
            int year) {}

    /** GET https://api.openf1.org/v1/meetings?year=2024 — the normal way: retrieve() */
    public List<Meeting> meetings(int year) {
        return http.get()
                .uri(uri -> uri.path("/meetings").queryParam("year", year).build())
                .retrieve() // any 4xx/5xx throws an exception (RestClientResponseException)
                // A List<Meeting> needs a ParameterizedTypeReference (Java erases generic types).
                .body(new ParameterizedTypeReference<List<Meeting>>() {});
    }

    /**
     * The same call, but deciding per status code yourself: exchange().
     * OpenF1 quirk: "no data" is answered with 404 and body {"detail":"No results found."} —
     * we want an empty list for that, and still an exception for real errors.
     */
    public List<Meeting> meetingsOrEmpty(int year) {
        return http.get()
                .uri(uri -> uri.path("/meetings").queryParam("year", year).build())
                .exchange((request, response) -> {
                    if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                        return List.of();
                    }
                    if (response.getStatusCode().isError()) {
                        throw new IllegalStateException("OpenF1 answered " + response.getStatusCode());
                    }
                    return response.bodyTo(new ParameterizedTypeReference<List<Meeting>>() {});
                });
    }
}

// Single object instead of a list:   .retrieve().body(Meeting.class)
// Need status/headers too:          .retrieve().toEntity(Meeting.class)
// POST with a JSON body:            http.post().uri("/x").body(request).retrieve().toBodilessEntity()
// Real APIs have rate limits (OpenF1 free tier ≈ 3 requests/second): pause between calls in loops.
