// TEMPLATE 04b — An entity with a relationship (many-to-one)
// Where it goes: src/main/java/com/telemetrix/<feature>/Book.java

package com.telemetrix.library;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The "many" side: many books belong to one author. This side OWNS the foreign key column.
    // Instead of a Long authorId you hold the Author object; Hibernate reads/writes author_id.
    // Always use LAZY here (the default for @ManyToOne is EAGER, which loads the author on every
    // query, even when you don't need it).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id")
    private Author author;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, unique = true)
    private String isbn;

    private LocalDate publishedOn;
    private Integer pages;

    protected Book() {}

    public Book(Author author, String title, String isbn) {
        this.author = author;
        this.title = title;
        this.isbn = isbn;
    }

    public Long getId() { return id; }
    public Author getAuthor() { return author; }
    public String getTitle() { return title; }
    public String getIsbn() { return isbn; }
    public LocalDate getPublishedOn() { return publishedOn; }
    public Integer getPages() { return pages; }

    public void setTitle(String title) { this.title = title; }
    public void setPublishedOn(LocalDate publishedOn) { this.publishedOn = publishedOn; }
    public void setPages(Integer pages) { this.pages = pages; }
}

// ─── Other relationship annotations ─────────────────────────────────────────────────────────────
//   @OneToOne   — e.g. Driver ↔ DriverProfile
//   @ManyToMany — e.g. Driver ↔ Team over the years; needs a join table
//                 (@JoinTable(name = "driver_teams", …)). Often clearer as its own entity instead.
//
// ─── A trap to know about: N+1 queries ──────────────────────────────────────────────────────────
// Loading 20 books and then calling book.getAuthor().getName() on each runs 1 + 20 queries.
// The fix is to load them together: a "JOIN FETCH" query or @EntityGraph (template 05).
