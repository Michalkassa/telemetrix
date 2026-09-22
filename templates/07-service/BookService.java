// TEMPLATE 07 — A service (business logic)
// Where it goes: src/main/java/com/telemetrix/<feature>/BookService.java
//
// THE LAYERS (a request travels down and the answer comes back up):
//
//     HTTP ──▶ Controller   – HTTP only: URLs, params, status codes, DTOs in/out   (template 08)
//                 │
//                 ▼
//              Service      – the rules: "ISBN must be unique", "load author, then save book"
//                 │           transactions live here
//                 ▼
//              Repository   – database access only                                (template 05)
//
// Keep each layer to its job. A controller never talks to a repository directly; a repository
// never knows about HTTP.

package com.telemetrix.library;

import com.telemetrix.library.BookDtos.BookResponse;
import com.telemetrix.library.BookDtos.CreateBookRequest;
import com.telemetrix.library.BookDtos.UpdateBookRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service // a bean: Spring creates one instance and injects it wherever it is needed
// Every public method runs in a database transaction. readOnly = true is a cheap hint for reads;
// methods that write override it with a plain @Transactional.
@Transactional(readOnly = true)
public class BookService {

    // Logger: use this, never System.out.println. {} placeholders are filled in only if the
    // level is enabled, so logging is cheap when turned off.
    private static final Logger log = LoggerFactory.getLogger(BookService.class);

    // Dependencies as final fields…
    private final BookRepository books;
    private final AuthorRepository authors;

    // …received through the constructor. With a single constructor Spring injects automatically
    // (no @Autowired needed). In tests you can call this constructor with fakes.
    public BookService(BookRepository books, AuthorRepository authors) {
        this.books = books;
        this.authors = authors;
    }

    public BookResponse get(long id) {
        return books.findById(id)
                .map(BookResponse::from)
                .orElseThrow(() -> new NotFoundException("Book", id)); // → 404 (template 09)
    }

    public Page<BookResponse> listByAuthor(long authorId, Pageable pageable) {
        return books.findByAuthorId(authorId, pageable).map(BookResponse::from);
    }

    public List<BookResponse> search(String text) {
        return books.findByTitleContainingIgnoreCase(text).stream()
                .map(BookResponse::from)
                .toList();
    }

    // A write: @Transactional (not read-only). If ANY exception escapes the method, everything it
    // did in the database is rolled back — all or nothing.
    @Transactional
    public BookResponse create(CreateBookRequest request) {
        // Business rule → a clear error instead of a database constraint crash.
        if (books.existsByIsbn(request.isbn())) {
            throw new ConflictException("A book with ISBN " + request.isbn() + " already exists");
        }
        Author author = authors.findById(request.authorId())
                .orElseThrow(() -> new NotFoundException("Author", request.authorId()));

        Book book = new Book(author, request.title(), request.isbn());
        book.setPublishedOn(request.publishedOn());
        book.setPages(request.pages());

        Book saved = books.save(book); // INSERT; `saved` now has its generated id
        log.info("Created book {} ({})", saved.getId(), saved.getIsbn());
        return BookResponse.from(saved);
    }

    @Transactional
    public BookResponse update(long id, UpdateBookRequest request) {
        Book book = books.findById(id).orElseThrow(() -> new NotFoundException("Book", id));
        book.setTitle(request.title());
        book.setPublishedOn(request.publishedOn());
        book.setPages(request.pages());
        // No save() call needed! Inside a transaction a loaded entity is "managed": Hibernate
        // notices the changed fields and runs the UPDATE when the transaction commits.
        // ("dirty checking")
        return BookResponse.from(book);
    }

    @Transactional
    public void delete(long id) {
        if (!books.existsById(id)) {
            throw new NotFoundException("Book", id);
        }
        books.deleteById(id);
    }
}
