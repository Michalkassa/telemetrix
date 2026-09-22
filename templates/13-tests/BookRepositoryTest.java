// TEMPLATE 13c — JPA slice test: repositories against a real database
// Where it goes: src/test/java/com/telemetrix/<feature>/BookRepositoryTest.java
//
// @DataJpaTest starts only JPA, Flyway and your repositories. Each test runs in a transaction
// that is ROLLED BACK at the end, so tests don't see each other's data.
// It uses a real Postgres from Testcontainers (13e): Flyway runs your real migrations, so this
// also proves that your entities match your tables.

package com.telemetrix.library;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@DataJpaTest
// By default @DataJpaTest swaps in an in-memory DB. "NONE" = keep ours (the Testcontainers one).
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class BookRepositoryTest {

    @Autowired BookRepository books;
    @Autowired AuthorRepository authors;

    @Test
    void findsBooksByTitleIgnoringCase() {
        // given
        var herbert = authors.save(new Author("Frank Herbert", "USA"));
        books.save(new Book(herbert, "Dune", "111"));
        books.save(new Book(herbert, "Dune Messiah", "222"));
        books.save(new Book(herbert, "The Dosadi Experiment", "333"));

        // when
        var found = books.findByTitleContainingIgnoreCase("dune");

        // then
        assertThat(found).extracting(Book::getTitle)
                .containsExactlyInAnyOrder("Dune", "Dune Messiah");
    }

    @Test
    void isbnLookup() {
        var herbert = authors.save(new Author("Frank Herbert", "USA"));
        books.save(new Book(herbert, "Dune", "111"));

        assertThat(books.findByIsbn("111")).isPresent();
        assertThat(books.existsByIsbn("999")).isFalse();
    }
}
