// TEMPLATE 06 — DTOs (Data Transfer Objects): the JSON your API receives and returns
// Where it goes: src/main/java/com/telemetrix/<feature>/  — usually one record per file
//               (BookResponse.java, CreateBookRequest.java). Grouped in one file here to read easily.
//
// WHY NOT RETURN THE ENTITY DIRECTLY?
//   • The API would change whenever the table changes (and leak columns you never meant to show).
//   • Lazy relationships (book.author) blow up or trigger extra queries during JSON conversion.
//   • What a client may SEND (no id, no createdAt) differs from what you RETURN.
// So: entities stay inside the app; records go over the wire.
//
// Needs dependency (for the @NotBlank etc. annotations):
//     <dependency>
//         <groupId>org.springframework.boot</groupId>
//         <artifactId>spring-boot-starter-validation</artifactId>
//     </dependency>

package com.telemetrix.library;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public final class BookDtos {

    private BookDtos() {}

    // ─── Response: what GET returns ─────────────────────────────────────────────────────────────
    // A record = immutable class with constructor, getters, equals/hashCode/toString generated.
    // Jackson turns each component into a JSON field:
    //   {"id":1,"title":"…","isbn":"…","publishedOn":"2024-05-01","pages":320,"authorName":"…"}
    public record BookResponse(
            Long id,
            String title,
            String isbn,
            LocalDate publishedOn,
            Integer pages,
            String authorName) {

        // Mapping entity → DTO. A static factory keeps it next to the DTO. (Bigger projects use
        // a generator like MapStruct; by hand is clearest while learning.)
        public static BookResponse from(Book book) {
            return new BookResponse(
                    book.getId(),
                    book.getTitle(),
                    book.getIsbn(),
                    book.getPublishedOn(),
                    book.getPages(),
                    book.getAuthor().getName());
        }
    }

    // ─── Request: what POST sends ───────────────────────────────────────────────────────────────
    // Bean Validation annotations describe what is allowed. They are checked when the controller
    // parameter is marked @Valid (template 08); a violation becomes HTTP 400 automatically.
    public record CreateBookRequest(
            @NotNull Long authorId,
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 20) String isbn,
            @PastOrPresent LocalDate publishedOn,
            @Positive Integer pages) {}

    // ─── Request: what PUT/PATCH sends ──────────────────────────────────────────────────────────
    // Different fields than create (e.g. ISBN may not change) → a separate record.
    public record UpdateBookRequest(
            @NotBlank @Size(max = 255) String title,
            @PastOrPresent LocalDate publishedOn,
            @Positive Integer pages) {}
}

// Other common constraints: @Email  @Min(1) @Max(10)  @Pattern(regexp = "…")  @Future  @NotEmpty (lists)
