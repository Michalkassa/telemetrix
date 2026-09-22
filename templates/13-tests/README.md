# Template 13 — Tests

Where they go: `src/test/java/com/telemetrix/<feature>/…Test.java`. The package mirrors the class
under test. Run them all with `./mvnw test`, or one with `./mvnw test -Dtest=BookServiceTest`.

There are four kinds, from fastest to slowest. Write many of the fast ones and a few of the slow ones.

| File | Kind | Starts | Use it for |
|------|------|--------|------------|
| `BookServiceTest.java` | **Unit test** | Nothing — plain Java + Mockito | Business rules in services |
| `BookControllerTest.java` | **Web slice** `@WebMvcTest` | Only the web layer, no DB | URLs, params, JSON, status codes, validation |
| `BookRepositoryTest.java` | **JPA slice** `@DataJpaTest` | Only JPA + a real Postgres | Your custom queries, and that the entities match the migrations |
| `ApplicationIntegrationTest.java` | **Full app** `@SpringBootTest` | Everything + real Postgres + a real HTTP port | One end-to-end path through all layers |

`TestcontainersConfiguration.java` is shared by the last two. It starts a throwaway Postgres in
Docker for the test run, so tests never touch your dev database.

## Dependencies (`pom.xml`, all `<scope>test</scope>`)

```xml
<!-- already there: JUnit 5, AssertJ, Mockito, MockMvc -->
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webmvc-test</artifactId><scope>test</scope></dependency>
<!-- for @DataJpaTest -->
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa-test</artifactId><scope>test</scope></dependency>
<!-- for Testcontainers -->
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-testcontainers</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.testcontainers</groupId><artifactId>testcontainers-junit-jupiter</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.testcontainers</groupId><artifactId>testcontainers-postgresql</artifactId><scope>test</scope></dependency>
<!-- for RestTestClient in @SpringBootTest(webEnvironment = RANDOM_PORT) -->
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-resttestclient</artifactId><scope>test</scope></dependency>
```

## Every test has the same shape: given / when / then

```java
@Test
void rejectsDuplicateIsbn() {
    // given — set up the situation
    // when  — do the one thing being tested
    // then  — check the result
}
```

Name tests after the behaviour (`rejectsDuplicateIsbn`), not the method (`testCreate`).
