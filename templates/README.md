# Spring Boot templates

A reference for every part of a Spring Boot app, written as **real code with comments that explain
each line**. Nothing here is compiled or run; it is for reading and copying. You write the
Telemetrix code yourself in `backend/`.

The example domain is a tiny library (**authors** and **books**), so the templates teach the
patterns without doing the Telemetrix work for you. Swap `Book` for `Race`, `Author` for
`Driver`, and so on.

> All templates were checked by copying them into a scratch copy of this project on Spring Boot
> 4.1.1 / Java 25: everything compiles, the 9 tests pass, and the Docker image builds and serves the
> API.

## Read them in this order

A request travels **Controller → Service → Repository → Database** and the answer comes back up.
The templates follow the order you would build a feature in: database first, HTTP last.

| # | Folder | What you learn |
|---|--------|----------------|
| 01 | `01-application/` | The main class, `@SpringBootApplication`, what a *bean* is, dependency injection |
| 02 | `02-configuration/` | `application.properties`, `${ENV:default}`, logging, profiles |
| 03 | `03-migration/` | Flyway migrations: how the database schema is created and changed |
| 04 | `04-entity/` | JPA entities: a Java class ⇄ a table, ids, columns, `@ManyToOne`/`@OneToMany` |
| 05 | `05-repository/` | Spring Data repositories: derived queries, `@Query`, paging, N+1 |
| 06 | `06-dto/` | Request/response records, why not to expose entities, validation annotations |
| 07 | `07-service/` | Business logic, `@Transactional`, logging, dirty checking |
| 08 | `08-controller/` | REST endpoints: GET/POST/PUT/DELETE, path/query params, status codes |
| 09 | `09-errors/` | Your own exceptions + `@RestControllerAdvice` → 404/409 as JSON |
| 10 | `10-config-properties/` | `@ConfigurationProperties` records, `@Configuration` + `@Bean` |
| 11 | `11-http-client/` | Calling an external API (OpenF1) with `RestClient` |
| 12 | `12-scheduling/` | Background jobs with `@Scheduled` |
| 13 | `13-tests/` | Unit, web-slice, JPA-slice and full integration tests (+ Testcontainers) |
| 14 | `14-docker/` | Multi-stage `Dockerfile`, `.dockerignore`, backend + DB in Compose |

## Dependencies each template needs

Already in `backend/pom.xml`: web MVC, JPA, Flyway, the PostgreSQL driver, and the web-MVC test
starter. You add the rest when you first use them:

| For | Add to `pom.xml` |
|-----|------------------|
| Validation (`@NotBlank`, `@Valid`) — 06, 08, 10 | `spring-boot-starter-validation` |
| `RestClient` — 10b, 11 | `spring-boot-starter-restclient` |
| Tests 13c–13e | see `13-tests/README.md` |

(All `groupId` `org.springframework.boot`, no `<version>`: the parent pom manages versions.)

## Notes

- **Packages.** Every template says `package com.telemetrix.library;` so the set compiles
  together. The *Where it goes* line at the top of each file says where it belongs in a real
  project, e.g. the shared exceptions go in `com.telemetrix.common`, and a Telemetrix feature
  gets its own package like `com.telemetrix.race`.
- **One package per feature.** Put a feature's entity, repository, service, DTOs and
  controller in the same package, instead of splitting them into `controllers/`, `services/`,
  `repositories/` folders.
- **Where to look things up:** [Spring Boot reference](https://docs.spring.io/spring-boot/) ·
  [Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/) ·
  [Spring Framework (MVC, RestClient)](https://docs.spring.io/spring-framework/reference/)
