// TEMPLATE 13b — Web slice test: the HTTP layer only
// Where it goes: src/test/java/com/telemetrix/<feature>/BookControllerTest.java
//
// @WebMvcTest starts just the MVC machinery for the listed controller (plus @RestControllerAdvice
// classes and JSON). No server port, no database, no services — the service is a mock.
// MockMvcTester sends fake requests through it. Checks URL mapping, params, validation, JSON and
// status codes.

package com.telemetrix.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.telemetrix.library.BookDtos.BookResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(BookController.class)
class BookControllerTest {

    @Autowired MockMvcTester mvc;       // provided by @WebMvcTest

    @MockitoBean BookService bookService; // puts a Mockito fake into Spring in place of the real bean

    @Test
    void returnsBookAsJson() {
        given(bookService.get(1)).willReturn(
                new BookResponse(1L, "Dune", "123", null, 412, "Frank Herbert"));

        assertThat(mvc.get().uri("/api/books/1"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"id": 1, "title": "Dune", "authorName": "Frank Herbert"}
                        """); // lenient = extra fields in the response are OK
    }

    @Test
    void unknownBookIs404() {
        given(bookService.get(99)).willThrow(new NotFoundException("Book", 99));

        assertThat(mvc.get().uri("/api/books/99"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail").isEqualTo("Book 99 not found");
    }

    @Test
    void invalidBodyIs400() {
        // title is blank and authorId is missing → @Valid rejects it before the service is called
        assertThat(mvc.post().uri("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "", "isbn": "123"}
                                """))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
