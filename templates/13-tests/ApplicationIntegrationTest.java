// TEMPLATE 13d — Full integration test: the whole app, real HTTP, real database
// Where it goes: src/test/java/com/telemetrix/ApplicationIntegrationTest.java
//
// @SpringBootTest starts EVERYTHING, as in production: on a random free port, with a Testcontainers
// Postgres. Slowest kind — keep a few that walk one important path through all layers.

package com.telemetrix.library;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.library.BookDtos.BookResponse;
import com.telemetrix.library.BookDtos.CreateBookRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient // gives us a RestTestClient already pointed at the random port
@Import(TestcontainersConfiguration.class)
class ApplicationIntegrationTest {

    @Autowired RestTestClient http;
    @Autowired AuthorRepository authors; // any bean can be injected into a test

    @Test
    void createThenFetchABook() {
        var author = authors.save(new Author("Ursula K. Le Guin", "USA"));

        // POST through the real HTTP stack
        var created = http.post().uri("/api/books")
                .body(new CreateBookRequest(author.getId(), "The Dispossessed", "444", null, 387))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED)
                .expectBody(BookResponse.class)
                .returnResult().getResponseBody();

        // GET it back
        http.get().uri("/api/books/" + created.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody(BookResponse.class)
                .value(book -> assertThat(book.authorName()).isEqualTo("Ursula K. Le Guin"));
    }
}

// The minimal version — Initializr generates it — just checks that the app can start at all:
//
//     @SpringBootTest
//     @Import(TestcontainersConfiguration.class)
//     class ApplicationTests {
//         @Test void contextLoads() {}
//     }
