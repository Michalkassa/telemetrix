// TEMPLATE 01 — The main class
// Where it goes: src/main/java/com/telemetrix/TelemetrixBackendApplication.java  (you already have it)
//
// Every Spring Boot app has exactly one of these. Running main() starts everything:
// the web server, the database connection pool, Flyway, and all your own classes.

package com.telemetrix;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// @SpringBootApplication is three annotations in one:
//   @Configuration            → this class may define beans (see template 10)
//   @EnableAutoConfiguration  → "look at the jars on the classpath and configure them for me"
//                               (webmvc jar → start Tomcat; postgres driver + datasource url → a
//                               connection pool; flyway jar → run migrations on startup; …)
//   @ComponentScan            → find every @Component / @Service / @Repository / @RestController /
//                               @Configuration in THIS package and all packages below it.
//
// ⇒ Consequence: keep this class in the top package (com.telemetrix). A class in a package that
//   is not below it (e.g. com.other) is simply never found.
@SpringBootApplication
// Optional extras — add them only when you use the feature:
@ConfigurationPropertiesScan // finds @ConfigurationProperties records (template 10)
@EnableScheduling            // turns on @Scheduled methods (template 12)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

// ─── What is a "bean"? ──────────────────────────────────────────────────────────────────────────
// A bean is an object that Spring creates and manages for you — normally one instance per class
// (a singleton). You never write `new RaceService(...)`: you declare what you need in a
// constructor and Spring passes it in. That is "dependency injection":
//
//     @Service
//     class RaceService {
//         private final RaceRepository repo;
//         RaceService(RaceRepository repo) { this.repo = repo; }   // Spring supplies repo
//     }
//
// Why: classes don't build their own dependencies, so in a test you can pass a fake instead.
