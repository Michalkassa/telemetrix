// TEMPLATE 05 — A Spring Data repository (database access)
// Where it goes: src/main/java/com/telemetrix/<feature>/BookRepository.java
//
// You write only an INTERFACE. At startup Spring Data generates the implementation class and
// registers it as a bean, so a service can simply ask for a BookRepository in its constructor.

package com.telemetrix.library;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// JpaRepository<EntityType, IdType> already gives you, for free:
//   save(entity)  saveAll(list)  findById(id)  findAll()  existsById(id)  count()
//   deleteById(id)  findAll(Pageable)  findAll(Sort) …
public interface BookRepository extends JpaRepository<Book, Long> {

    // ─── 1. Derived queries: Spring reads the METHOD NAME and writes the SQL ────────────────────
    // findBy + field name (+ And/Or/OrderBy/…). Field names are the JAVA names, not columns.

    Optional<Book> findByIsbn(String isbn);                    // WHERE isbn = ?     (0 or 1 row)

    List<Book> findByAuthorIdOrderByPublishedOnDesc(Long id);  // WHERE author_id = ? ORDER BY …

    List<Book> findByTitleContainingIgnoreCase(String text);   // WHERE lower(title) LIKE %text%

    boolean existsByIsbn(String isbn);                         // SELECT count(*) > 0 …

    // Pagination: pass Pageable (page number, size, sort) and get a Page back (content + totals).
    // The controller builds it from ?page=0&size=20&sort=title  (template 08).
    Page<Book> findByAuthorId(Long authorId, Pageable pageable);

    // ─── 2. @Query: write the query yourself when the name would get silly ──────────────────────
    // This is JPQL: it talks about ENTITIES and FIELDS (Book, b.author.name), not tables/columns.
    @Query("select b from Book b where b.pages > :minPages and b.author.country = :country")
    List<Book> findLongBooksFrom(@Param("country") String country, @Param("minPages") int minPages);

    // Plain SQL instead of JPQL: nativeQuery = true. Use for Postgres-only features.
    @Query(value = "select * from books where published_on >= now() - interval '1 year'",
           nativeQuery = true)
    List<Book> findPublishedInLastYear();

    // ─── 3. Avoid N+1: load the author in the same query ────────────────────────────────────────
    @EntityGraph(attributePaths = "author")
    List<Book> findAllByOrderByTitle();

    // ─── 4. Projections: return only some columns, straight into a record ───────────────────────
    // Faster than loading whole entities when you just need a summary.
    @Query("select new com.telemetrix.library.BookRepository$TitleAndPages(b.title, b.pages) "
            + "from Book b where b.author.id = :authorId")
    List<TitleAndPages> findTitlesByAuthor(@Param("authorId") Long authorId);

    record TitleAndPages(String title, Integer pages) {}
}

// One repository per "aggregate root" entity. An AuthorRepository would look the same:
//     public interface AuthorRepository extends JpaRepository<Author, Long> {}
