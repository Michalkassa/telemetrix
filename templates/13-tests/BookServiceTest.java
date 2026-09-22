// TEMPLATE 13a — Unit test: one class, fakes for everything around it
// Where it goes: src/test/java/com/telemetrix/<feature>/BookServiceTest.java
//
// No Spring at all: we call the constructor ourselves and pass Mockito fakes ("mocks") in.
// Runs in milliseconds. Best for business rules.

package com.telemetrix.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.telemetrix.library.BookDtos.CreateBookRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class) // lets Mockito create the @Mock fields
class BookServiceTest {

    @Mock BookRepository books;       // fake: returns nothing unless told otherwise
    @Mock AuthorRepository authors;

    @InjectMocks BookService service; // real BookService, built with the mocks above

    @Test
    void rejectsDuplicateIsbn() {
        // given — "when existsByIsbn is called with this ISBN, answer true"
        given(books.existsByIsbn("123")).willReturn(true);

        // when + then — calling create must throw
        assertThatThrownBy(() -> service.create(new CreateBookRequest(1L, "Dune", "123", null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("123");

        // and nothing was saved
        verify(books, never()).save(any());
    }

    @Test
    void createsBookForExistingAuthor() {
        var herbert = new Author("Frank Herbert", "USA");
        given(books.existsByIsbn("123")).willReturn(false);
        given(authors.findById(1L)).willReturn(Optional.of(herbert));
        given(books.save(any(Book.class))).willAnswer(call -> call.getArgument(0)); // echo it back

        var result = service.create(new CreateBookRequest(1L, "Dune", "123", null, 412));

        assertThat(result.title()).isEqualTo("Dune");
        assertThat(result.authorName()).isEqualTo("Frank Herbert");
    }

    @Test
    void unknownAuthorIsNotFound() {
        given(authors.findById(9L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateBookRequest(9L, "Dune", "123", null, null)))
                .isInstanceOf(NotFoundException.class);
    }
}
