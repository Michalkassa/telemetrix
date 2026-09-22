// TEMPLATE 08 — A REST controller (the HTTP API)
// Where it goes: src/main/java/com/telemetrix/<feature>/BookController.java
//
// Job: translate HTTP ⇄ Java. Read the URL/params/body, call the service, choose the status code.
// No business logic and no repositories in here.

package com.telemetrix.library;

import com.telemetrix.library.BookDtos.BookResponse;
import com.telemetrix.library.BookDtos.CreateBookRequest;
import com.telemetrix.library.BookDtos.UpdateBookRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController                  // = @Controller + "return values are the response body (JSON)"
@RequestMapping("/api/books")    // prefix for every method below
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    // GET /api/books/42
    // @PathVariable takes {id} from the URL and converts it to long ("abc" → 400 automatically).
    @GetMapping("/{id}")
    public BookResponse get(@PathVariable long id) {
        return bookService.get(id);                 // returned object → JSON, status 200
    }

    // GET /api/books?q=dune
    // @RequestParam reads ?q=… ; required by default (missing → 400). Make it optional with
    // required = false or defaultValue = "…".
    @GetMapping
    public List<BookResponse> search(@RequestParam("q") String text) {
        return bookService.search(text);
    }

    // GET /api/books/by-author/7?page=0&size=20&sort=title,asc
    // Spring builds the Pageable from page/size/sort params; @PageableDefault sets defaults.
    // With spring.data.web.pageable.serialization-mode=via-dto (template 02) the JSON is stable:
    //   {"content":[…rows…], "page":{"size":20,"number":0,"totalElements":57,"totalPages":3}}
    // Without that setting Spring dumps its internal Page object (and logs a warning about it).
    @GetMapping("/by-author/{authorId}")
    public Page<BookResponse> byAuthor(@PathVariable long authorId,
                                       @PageableDefault(size = 20, sort = "title") Pageable pageable) {
        return bookService.listByAuthor(authorId, pageable);
    }

    // POST /api/books   body: {"authorId":1,"title":"Dune","isbn":"9780441013593"}
    // @RequestBody: JSON body → record.  @Valid: run the DTO's validation annotations first;
    // if any fail, this method is never called and the client gets 400 with the details.
    // REST convention for create: 201 Created + a Location header pointing at the new resource.
    @PostMapping
    public ResponseEntity<BookResponse> create(@Valid @RequestBody CreateBookRequest request) {
        BookResponse created = bookService.create(request);
        return ResponseEntity
                .created(URI.create("/api/books/" + created.id()))
                .body(created);
    }

    // PUT /api/books/42  — replace the editable fields
    @PutMapping("/{id}")
    public BookResponse update(@PathVariable long id, @Valid @RequestBody UpdateBookRequest request) {
        return bookService.update(id, request);
    }

    // DELETE /api/books/42  — 204 No Content: success, nothing to send back.
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        bookService.delete(id);
    }
}

// ─── Status codes you will use ──────────────────────────────────────────────────────────────────
//   200 OK            normal success (the default)
//   201 Created       after POST created something
//   204 No Content    success with empty body (DELETE)
//   400 Bad Request   invalid input (validation, wrong types)        → mostly automatic
//   404 Not Found     the id does not exist                          → template 09
//   409 Conflict      violates a rule (duplicate ISBN)               → template 09
//   500 Server Error  a bug: an exception nobody handled
//
// ─── Try it ─────────────────────────────────────────────────────────────────────────────────────
//   curl localhost:8080/api/books/1
//   curl -X POST localhost:8080/api/books -H 'Content-Type: application/json' \
//        -d '{"authorId":1,"title":"Dune","isbn":"9780441013593"}'
