// TEMPLATE 05b — The simplest possible repository
// Where it goes: src/main/java/com/telemetrix/<feature>/AuthorRepository.java

package com.telemetrix.library;

import org.springframework.data.jpa.repository.JpaRepository;

// No methods of our own needed: save/findById/findAll/… come from JpaRepository.
public interface AuthorRepository extends JpaRepository<Author, Long> {}
