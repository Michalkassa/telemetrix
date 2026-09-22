// TEMPLATE 04a — A JPA entity (a Java class mapped to a table)
// Where it goes: src/main/java/com/telemetrix/<feature>/Author.java
//
// One instance = one row of the `authors` table from template 03.
// Hibernate (the JPA implementation) reads/writes rows and turns them into these objects.

package com.telemetrix.library;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity                    // "Hibernate, manage this class"
@Table(name = "authors")   // table name; without it Hibernate would expect "author"
public class Author {

    // Primary key. IDENTITY = the database generates it (our BIGSERIAL column).
    // Use wrapper types (Long, Integer) — they can be null, meaning "not saved yet".
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Field names map to columns automatically: camelCase → snake_case (createdAt → created_at).
    // @Column is only needed to add constraints or rename: @Column(name = "full_name").
    @Column(nullable = false, length = 100)
    private String name;

    private String country;

    // insertable/updatable = false: the database fills it (DEFAULT now()), we only read it.
    @Column(insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    // The "one" side of one-to-many. mappedBy = the field name on Book that owns the foreign key.
    // Collections are LAZY by default: the books are only loaded when you touch this list.
    @OneToMany(mappedBy = "author")
    private List<Book> books = new ArrayList<>();

    // JPA REQUIRES a no-argument constructor (it creates the object, then fills the fields).
    // protected = only JPA should use it; your code uses the constructor below.
    protected Author() {}

    public Author(String name, String country) {
        this.name = name;
        this.country = country;
    }

    // Getters so other classes can read the fields. Setters only for what may change.
    // (Many projects generate these with Lombok's @Getter/@Setter; written out here so you see them.)
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCountry() { return country; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public List<Book> getBooks() { return books; }

    public void setName(String name) { this.name = name; }
    public void setCountry(String country) { this.country = country; }
}

// ─── Java ↔ PostgreSQL types ────────────────────────────────────────────────────────────────────
//   Long ↔ BIGINT/BIGSERIAL      Integer ↔ INTEGER      String ↔ VARCHAR/TEXT
//   Boolean ↔ BOOLEAN            BigDecimal ↔ NUMERIC   Double ↔ DOUBLE PRECISION
//   LocalDate ↔ DATE             OffsetDateTime / Instant ↔ TIMESTAMPTZ
//   an enum ↔ VARCHAR with @Enumerated(EnumType.STRING)   (never the default ORDINAL)
