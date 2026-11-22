# URL Shortener: Stage 2 Review (PostgreSQL persistence)

| | |
|---|---|
| Project | `com.malinda:url-shortener` (`E:\Projects\URL Shortener\url-shortener`) |
| Code reviewed | Branch `dev`, HEAD `2cebc34` "Refactor UrlServiceIMPL to retrieve original URL using code from LinkRepository". The working tree was clean when the session started |
| Review date | 2026-11-22 (local time UTC+05:30). Tests 20:03 to 20:22, manual checks 20:09 to 20:15 (run ID `20261122200904`) |
| Reviewer | Claude Code (Claude Opus 5.5), at the author's request |
| Compared against | `docs/reviews/stage-1-final-review.md` (Stage 1 final review, revision 3) |

## 1. Summary

**Persistence works: links survive an app crash and a recreated database container, and the Stage 1 contract is unchanged.** All 16 existing tests pass against a real PostgreSQL (Testcontainers, `postgres:18-alpine`), as do the 4 new persistence tests and 2 isolation checks.

**Exit checklist: 3 of 8 criteria fail, and they share one cause.** `Link` uses an assigned `@Id` with no way to tell Spring Data it is new. So `save()` merges: every create runs a `select ... where code=?` before its `insert`, and a code collision **silently overwrites** the existing link with an `UPDATE`, returning **201** (C1, observed in 2 failing tests and in the SQL log).

Other observations: with PostgreSQL down, a request hangs **30.5 s** and then returns **500** with a 24-frame stack trace. Two unused `Map`/`ConcurrentHashMap` imports are left over from Stage 1. `spring.jpa.open-in-view` is on by default (startup WARN).

I did not create, edit or delete anything under `src/main` in this session.

## 2. Stage 2 exit checklist

| Criterion | Result | Evidence |
|---|---|---|
| All tests pass against PostgreSQL | **FAIL** | `Tests run: 24, Failures: 2`. The 2 failures are the new code-collision tests (`CodeCollisionTest`, `CodeCollisionHttpTest`). All 16 pre-existing tests and the other 6 new tests pass (section 6.5) |
| Links survive an app crash and a recreated database container | **PASS** | After `taskkill /F` and `docker compose down`/`up` (container `5c43552a85fd` replaced by `35c6806b3f26`, volume kept), all 3 codes still return 302 with the original `Location` (section 7.5) |
| A re-POST after the restart returns 200 with the same code | **PASS** | `campaign/20261122200904/1`: `201 xKP9u6` before, `200 xKP9u6` after (sections 7.2, 7.5) |
| A new create runs no select-by-code before its insert | **FAIL** | Every new create logs 3 statements: `select ... where long_url=?`, **`select ... where code=?`**, `insert` (section 7.3) |
| A code collision fails instead of overwriting | **FAIL** | Repository: the second `saveAndFlush` runs `update links set long_url=? where code=?` and throws nothing. HTTP: the POST returns 201 with the taken code, and the old URL is replaced (section 6.5) |
| A 300-character URL works | **PASS** | `url300CharactersLong_isStoredAndRedirectedUnchanged` passes. Manual: 201 `y8MtqX`, 302 with a 300-character `Location`, and psql shows `length = 300` (sections 6.5, 7.7, 8) |
| The contract is unchanged from Stage 1 | **PASS** | All 23 Stage 1 contract cases give the same status, body shape and messages (section 12) |
| Nothing under `src/main` was modified in this session | **PASS** | `git status` lists only `pom.xml` and `src/test` files, and `git diff --stat -- src/main` is empty (section 6.1) |

## 3. Environment

Versions come from `./mvnw dependency:tree` (the resolved tree), not from the pom.

| Item | Value | Source |
|---|---|---|
| Spring Boot | 4.1.2 (`spring-boot-starter-parent`) | dependency tree: `org.springframework.boot:spring-boot:jar:4.1.2` |
| Spring Data JPA | 4.1.2 | dependency tree |
| Hibernate ORM | 7.4.13.Final | dependency tree `org.hibernate.orm:hibernate-core:jar:7.4.13.Final`; startup log `HHH000001` |
| Flyway | 12.4.0 (`flyway-core`, `flyway-database-postgresql`) | dependency tree |
| PostgreSQL JDBC driver | 42.7.14 (runtime scope) | dependency tree `org.postgresql:postgresql:jar:42.7.14:runtime` |
| Testcontainers | 2.0.5 (`testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-postgresql`, `testcontainers-jdbc`). The version comes from the Boot parent (`testcontainers.version` 2.0.5 via `testcontainers-bom`); the pom does not set it | dependency tree; `spring-boot-dependencies-4.1.2.pom` line 209 |
| Testcontainers helper image | `testcontainers/ryuk:0.14.0` | test log |
| PostgreSQL server | 18.6, image `postgres:18-alpine` (Compose and tests) | Flyway log `(PostgreSQL 18.6)`; `docker-compose.yaml` |
| Java | 21.0.7 (Oracle HotSpot, build 21.0.7+8-LTS-245) | `java -version` |
| Maven | 3.10.0 via Maven Wrapper | `./mvnw -v` |
| Docker | Client and Engine 28.1.1 (API 1.49), Docker Desktop 4.41.2 (191736), Compose v2.35.1-desktop.1, context `desktop-linux` | `docker version`, `docker compose version` |
| OS | Windows 11 Home Single Language 10.0.26200 (build 26200.9974); Git Bash; curl 8.14.1 | `ver`, `curl --version` |

**Git.** Branch `dev`, up to date with `origin/dev`. HEAD `2cebc34cf4154911c09251b0b69c4e3c9f7d3e59`. The working tree was clean at the start of the session. **No tag named `stage-1` exists**: `git tag -l` prints nothing, and `git ls-remote --tags origin` prints nothing either. The working-tree status at the end of the session:

```
 M pom.xml
 M src/test/java/com/malinda/url_shortener/ShortUrlBaseUrlTest.java
 M src/test/java/com/malinda/url_shortener/UrlShortenerAcceptanceTest.java
 M src/test/java/com/malinda/url_shortener/UrlShortenerApplicationTests.java
 M src/test/java/com/malinda/url_shortener/UrlShortenerErrorCasesTest.java
?? docs/reviews/stage-2-review.md
?? docs/reviews/stage-2-review.pdf
?? src/test/java/com/malinda/url_shortener/CodeCollisionHttpTest.java
?? src/test/java/com/malinda/url_shortener/CodeCollisionTest.java
?? src/test/java/com/malinda/url_shortener/LinkPersistenceTest.java
?? src/test/java/com/malinda/url_shortener/TestDatabaseIsolationTest.java
?? src/test/java/com/malinda/url_shortener/TestcontainersConfiguration.java
```

**Differences between the brief and the repository** (I used the repository's actual names):
- The Compose file is `docker-compose.yaml`, not `compose.yaml`.
- Compose uses `postgres:18-alpine`, so the tests use the same image. The brief says `postgres:18`, but its stated aim is "to match compose.yaml".
- The V1 column is `long_url`, not `url`. The brief's Step 3.8 query fails with `column "url" does not exist` (section 8), so I also ran it with `long_url`.

**Other processes on this machine.** Three pre-existing IntelliJ JVMs were running throughout and I did not touch them: PID 21080 (Maven importer), PID 22268 (database tool) and PID 16876. PID 16876 is the author's own debug run of `UrlShortenerApplication` on port 8081, which uses the Compose database. That app was affected whenever the Compose database was down: during the first test run (deliberately stopped, section 6.4), and during manual steps 3.4 and 3.7.

## 4. File tree of `src/`

```
src
    main
        java
            com
                malinda
                    url_shortener
                        UrlShortenerApplication.java
                        controller
                            UrlController.java
                        dto
                            ApiErrorResponse.java
                            CreateUrlRequest.java
                            CreateUrlResponse.java
                            CreateUrlResult.java
                        entity
                            Link.java
                        exceptions
                            GlobalExceptionHandler.java
                            InvalidCodeException.java
                            InvalidUrlException.java
                        repo
                            LinkRepository.java
                        service
                            IMPL
                                UrlServiceIMPL.java
                            UrlService.java
                        util
                            ShortCodeGenerator.java
                            UrlValidator.java
        resources
            application.yaml
            db
                migration
                    V1__create_links.sql
            static
            templates
    test
        java
            com
                malinda
                    url_shortener
                        CodeCollisionHttpTest.java
                        CodeCollisionTest.java
                        LinkPersistenceTest.java
                        ShortUrlBaseUrlTest.java
                        TestDatabaseIsolationTest.java
                        TestcontainersConfiguration.java
                        UrlShortenerAcceptanceTest.java
                        UrlShortenerApplicationTests.java
                        UrlShortenerErrorCasesTest.java
```

`resources/static/` and `resources/templates/` are empty directories. In `src/test`, I created `TestcontainersConfiguration`, `TestDatabaseIsolationTest`, `LinkPersistenceTest`, `CodeCollisionTest` and `CodeCollisionHttpTest` in this session. The other four test files already existed, and I changed them as described in section 6.3.

## 5. Full contents of every file under `src/main`, and `pom.xml`

These are the files exactly as on disk at HEAD `2cebc34` (no `src/main` changes in the working tree). Line numbers in section 9 refer to these files.

### 5.1 `src/main/java/com/malinda/url_shortener/UrlShortenerApplication.java`
```java
package com.malinda.url_shortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UrlShortenerApplication {

	public static void main(String[] args) {
		SpringApplication.run(UrlShortenerApplication.class, args);
	}

}
```

### 5.2 `src/main/java/com/malinda/url_shortener/controller/UrlController.java`
```java
package com.malinda.url_shortener.controller;

import com.malinda.url_shortener.dto.CreateUrlRequest;
import com.malinda.url_shortener.dto.CreateUrlResponse;
import com.malinda.url_shortener.dto.CreateUrlResult;
import com.malinda.url_shortener.service.UrlService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
public class UrlController {

    private final UrlService urlService;

    public UrlController(UrlService urlService) {
        this.urlService = urlService;
    }

    @PostMapping("/api/urls")
    public ResponseEntity<CreateUrlResponse> createShortUrl(@RequestBody CreateUrlRequest request) {
        CreateUrlResult response = urlService.createShortUrl(request.url());

        if (response.created()){
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(response.response());
        } else {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(response.response());
        }
    }

    @GetMapping("/{code}")
    public  ResponseEntity<Void> redirectToOriginalUrl(@PathVariable String code) {
        String response = urlService.redirectToOriginalUrl(code);
        return  ResponseEntity
                .status(HttpStatus.FOUND)
                .location(URI.create(response))
                .build();
    }
}
```

### 5.3 `src/main/java/com/malinda/url_shortener/dto/ApiErrorResponse.java`
```java
package com.malinda.url_shortener.dto;

public record ApiErrorResponse(String message) {
}
```

### 5.4 `src/main/java/com/malinda/url_shortener/dto/CreateUrlRequest.java`
```java
package com.malinda.url_shortener.dto;

public record CreateUrlRequest(String url) {}
```

### 5.5 `src/main/java/com/malinda/url_shortener/dto/CreateUrlResponse.java`
```java
package com.malinda.url_shortener.dto;

public record CreateUrlResponse(String code, String shortUrl) {}
```

### 5.6 `src/main/java/com/malinda/url_shortener/dto/CreateUrlResult.java`
```java
package com.malinda.url_shortener.dto;

public record CreateUrlResult(CreateUrlResponse response, boolean created) {
}
```

### 5.7 `src/main/java/com/malinda/url_shortener/entity/Link.java`
```java
package com.malinda.url_shortener.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "links")
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class Link {
    @Id
    private String code;

    @Column(name = "long_url")
    private String longUrl;
}
```

### 5.8 `src/main/java/com/malinda/url_shortener/exceptions/GlobalExceptionHandler.java`
```java
package com.malinda.url_shortener.exceptions;

import com.malinda.url_shortener.dto.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidUrlException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidUrlException(InvalidUrlException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(InvalidCodeException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCodeException(InvalidCodeException e) {
        return  ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(e.getMessage()));
    }
}
```

### 5.9 `src/main/java/com/malinda/url_shortener/exceptions/InvalidCodeException.java`
```java
package com.malinda.url_shortener.exceptions;

public class InvalidCodeException extends RuntimeException{
    public InvalidCodeException(String message) {
        super(message);
    }
}
```

### 5.10 `src/main/java/com/malinda/url_shortener/exceptions/InvalidUrlException.java`
```java
package com.malinda.url_shortener.exceptions;

public class InvalidUrlException extends RuntimeException{
    public InvalidUrlException(String message) {
        super(message);
    }
}
```

### 5.11 `src/main/java/com/malinda/url_shortener/repo/LinkRepository.java`
```java
package com.malinda.url_shortener.repo;

import com.malinda.url_shortener.entity.Link;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LinkRepository extends JpaRepository<Link, String> {
    Optional<Link> findByLongUrl(String longUrl);
    Optional<Link> findByCode(String code);
}
```

### 5.12 `src/main/java/com/malinda/url_shortener/service/UrlService.java`
```java
package com.malinda.url_shortener.service;

import com.malinda.url_shortener.dto.CreateUrlResult;

public interface UrlService {
    CreateUrlResult createShortUrl(String longUrl);

    String redirectToOriginalUrl(String code);
}
```

### 5.13 `src/main/java/com/malinda/url_shortener/service/IMPL/UrlServiceIMPL.java`
```java
package com.malinda.url_shortener.service.IMPL;

import com.malinda.url_shortener.dto.CreateUrlResponse;
import com.malinda.url_shortener.dto.CreateUrlResult;
import com.malinda.url_shortener.entity.Link;
import com.malinda.url_shortener.exceptions.InvalidCodeException;
import com.malinda.url_shortener.repo.LinkRepository;
import com.malinda.url_shortener.service.UrlService;
import com.malinda.url_shortener.util.ShortCodeGenerator;
import com.malinda.url_shortener.util.UrlValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UrlServiceIMPL implements UrlService {

    private final LinkRepository linkRepository;

    @Value("${app.base-url}")
    private String baseUrl;

    public UrlServiceIMPL(LinkRepository linkRepository) {
        this.linkRepository = linkRepository;
    }

    @Override
    public CreateUrlResult createShortUrl(String longUrl) {
        UrlValidator.validateUrl(longUrl);

        Optional<Link> existingLink = linkRepository.findByLongUrl(longUrl);

            if (existingLink.isPresent()) {

                String existingCode = existingLink.get().getCode();
                return new CreateUrlResult(
                        new CreateUrlResponse(
                                existingCode,
                                buildShortUrl(existingCode)),
                        false
                );
            }

        String code = ShortCodeGenerator.generateShortCode();
        Link link = new Link(code,longUrl);
        linkRepository.save(link);
        return new CreateUrlResult(
                new CreateUrlResponse(
                        code,
                        buildShortUrl(code)
                ),
                true
        );
    }

    @Override
    public String redirectToOriginalUrl(String code) {
        Optional<Link> link = linkRepository.findByCode(code);
        if (link.isEmpty()) {
            throw new InvalidCodeException("Unknown code");
        } else {
            return link.get().getLongUrl();
        }
    }

    private String buildShortUrl(String code) {
        String normalizedBaseUrl = baseUrl.endsWith("/")
                ? baseUrl
                : baseUrl + "/";

        return normalizedBaseUrl + code;
    }
}
```

### 5.14 `src/main/java/com/malinda/url_shortener/util/ShortCodeGenerator.java`
```java
package com.malinda.url_shortener.util;

import java.security.SecureRandom;

public class ShortCodeGenerator {
    private static final String CHARACTERS =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static final SecureRandom secureRandom = new SecureRandom();

    public static String generateShortCode() {
        StringBuilder shortCode = new StringBuilder();

        for (int i = 0; i < 6; i++) {
            int randomIndex = secureRandom.nextInt(CHARACTERS.length());
            shortCode.append(CHARACTERS.charAt(randomIndex));
        }

        return shortCode.toString();
    }
}
```

### 5.15 `src/main/java/com/malinda/url_shortener/util/UrlValidator.java`
```java
package com.malinda.url_shortener.util;

import com.malinda.url_shortener.exceptions.InvalidUrlException;

import java.net.URI;

public class UrlValidator {
    public static void validateUrl(String longUrl) {
        if (longUrl == null || longUrl.isBlank()) {
            throw new InvalidUrlException("URL cannot be empty");
        }

        try {
            URI uri = URI.create(longUrl);

            String schema = uri.getScheme();

            if (schema == null || (!schema.equalsIgnoreCase("http") && !schema.equalsIgnoreCase("https")) || uri.getHost() == null) {
                throw new InvalidUrlException("Invalid URL format");
            }
        } catch (IllegalArgumentException e) {
            throw new InvalidUrlException("Malformed URL");
        }
    }
}
```

### 5.16 `src/main/resources/application.yaml`
```yaml
spring:
  application:
    name: url-shortener

  datasource:
    url: jdbc:postgresql://localhost:5432/shortener
    username: shortener
    password: shortener

  jpa:
    hibernate:
      ddl-auto: validate

server:
  port: 8081

app:
  base-url: http://localhost:8081/
```

### 5.17 `src/main/resources/db/migration/V1__create_links.sql`
```sql
CREATE TABLE links(
    code VARCHAR(10) PRIMARY KEY,
    long_url TEXT NOT NULL
);
```

### 5.18 `pom.xml` (working tree, including the test dependencies added in this session)
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<parent>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-parent</artifactId>
		<version>4.1.2</version>
		<relativePath/> <!-- lookup parent from repository -->
	</parent>
	<groupId>com.malinda</groupId>
	<artifactId>url-shortener</artifactId>
	<version>0.0.1-SNAPSHOT</version>
	<name/>
	<description/>
	<url/>
	<licenses>
		<license/>
	</licenses>
	<developers>
		<developer/>
	</developers>
	<scm>
		<connection/>
		<developerConnection/>
		<tag/>
		<url/>
	</scm>
	<properties>
		<java.version>21</java.version>
	</properties>
	<dependencies>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-webmvc</artifactId>
		</dependency>

		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-webmvc-test</artifactId>
			<scope>test</scope>
		</dependency>

		<!-- Spring Data JPA -->
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-jpa</artifactId>
		</dependency>

		<!--PostgreSQL-->
		<dependency>
			<groupId>org.postgresql</groupId>
			<artifactId>postgresql</artifactId>
			<scope>runtime</scope>
		</dependency>

		<!-- Flyway Migration -->
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-flyway</artifactId>
		</dependency>

		<!-- PostgreSQL support for Flyway -->
		<dependency>
			<groupId>org.flywaydb</groupId>
			<artifactId>flyway-database-postgresql</artifactId>
		</dependency>

		<!-- Lombok -->
		<dependency>
			<groupId>org.projectlombok</groupId>
			<artifactId>lombok</artifactId>
			<optional>true</optional>
		</dependency>

		<!-- Testcontainers (test only): throwaway PostgreSQL for the test suite -->
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-testcontainers</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.testcontainers</groupId>
			<artifactId>testcontainers-junit-jupiter</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.testcontainers</groupId>
			<artifactId>testcontainers-postgresql</artifactId>
			<scope>test</scope>
		</dependency>
	</dependencies>

	<build>
		<plugins>
			<plugin>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-maven-plugin</artifactId>
			</plugin>
		</plugins>
	</build>

</project>
```

## 6. Test changes, commands and results

### 6.1 `pom.xml` diff (the only pom change)

I added three test-scoped dependencies, with no versions, because the Boot parent manages them (Testcontainers 2.0.5). Nothing else in `pom.xml` changed. `spring-boot-testcontainers` provides `@ServiceConnection`. `testcontainers-postgresql` provides `org.testcontainers.postgresql.PostgreSQLContainer`, which moved to that package in Testcontainers 2.x. `testcontainers-junit-jupiter` is the Testcontainers JUnit 5 module.

```diff
diff --git a/pom.xml b/pom.xml
index d9a2d11..6f3182e 100644
--- a/pom.xml
+++ b/pom.xml
@@ -72,6 +72,23 @@
 			<artifactId>lombok</artifactId>
 			<optional>true</optional>
 		</dependency>
+
+		<!-- Testcontainers (test only): throwaway PostgreSQL for the test suite -->
+		<dependency>
+			<groupId>org.springframework.boot</groupId>
+			<artifactId>spring-boot-testcontainers</artifactId>
+			<scope>test</scope>
+		</dependency>
+		<dependency>
+			<groupId>org.testcontainers</groupId>
+			<artifactId>testcontainers-junit-jupiter</artifactId>
+			<scope>test</scope>
+		</dependency>
+		<dependency>
+			<groupId>org.testcontainers</groupId>
+			<artifactId>testcontainers-postgresql</artifactId>
+			<scope>test</scope>
+		</dependency>
 	</dependencies>
 
 	<build>
```

### 6.2 How the tests are wired to PostgreSQL

- **`TestcontainersConfiguration`** is a `@TestConfiguration` with one `@Bean @ServiceConnection PostgreSQLContainer` (`postgres:18-alpine`). Spring Boot starts the container and derives `JdbcConnectionDetails` from it. Those override `spring.datasource.*` from `application.yaml`, so the DataSource, Flyway and Hibernate all use the container.
- **Every `@SpringBootTest` class** has `@Import(TestcontainersConfiguration.class)`.
- **Schema.** Flyway creates it from `db/migration` in each container. The log shows `Migrating schema "public" to version "1 - create links"` once per container. No test creates tables.
- **Containers.** The container is a bean, so each distinct cached application context gets its own container. The suite has three contexts:
  1. `@SpringBootTest` + MockMvc
  2. plain `@SpringBootTest`
  3. `ShortUrlBaseUrlTest`, which has its own `app.base-url` property

  So each run starts 3 containers, on random host ports (`jdbc:postgresql://localhost:65439/test`, `:65457/test` and `:63680/test` in the final run). None of them is `localhost:5432/shortener`.
- **Isolation evidence.**
  - `TestDatabaseIsolationTest` asserts that the live connection URL equals the container's JDBC URL and does not contain `localhost:5432/shortener`, and that `current_database()` is the container's database (`test`), not `shortener`.
  - The first full run was made with the Compose database **stopped** (`docker compose stop`, then `docker ps` showed no running container), and the context still loaded and every pre-existing test passed.
  - `grep -c 'localhost:5432'` over that run's log returns 0.

### 6.3 Changes to existing test files, and why

No assertion was removed, weakened or rewritten. Every changed assertion line still compares against the same variable or constant as before; only the input URLs got a unique suffix.

| File | Change | Why |
|---|---|---|
| `UrlShortenerApplicationTests` | Added `@Import(TestcontainersConfiguration.class)` (and its import) | Without it, the context would connect to the Compose database |
| `UrlShortenerAcceptanceTest` | Added `@Import(...)`. Added `RUN = UUID.randomUUID()` and appended `&run=<RUN>` or `?run=<RUN>` to all five stored URLs (`TRACKING_URL`, `ENCODED_URL`, `/some/page`, `a.example.com/first`, `b.example.com/second`). Extended the Javadoc | Links now persist across methods and classes that share a context. `ENCODED_URL` keeps `q=hello%20world` intact, so the no-double-encoding check is unchanged |
| `UrlShortenerErrorCasesTest` | Added `@Import(...)`. The duplicate URL gets `&run=<UUID>`. Updated the Javadoc sentence that said "All test classes share one in-memory store" | The URL must be new for the first POST to return 201. The comment was no longer true. The 8 invalid inputs are never stored, so they are unchanged |
| `ShortUrlBaseUrlTest` | Added `@Import(...)`. The URL gets `?run=<UUID>`. Extended the Javadoc | Same reason |

The full diff of these four files:

```diff
diff --git a/src/test/java/com/malinda/url_shortener/ShortUrlBaseUrlTest.java b/src/test/java/com/malinda/url_shortener/ShortUrlBaseUrlTest.java
index a44c20f..9b2f370 100644
--- a/src/test/java/com/malinda/url_shortener/ShortUrlBaseUrlTest.java
+++ b/src/test/java/com/malinda/url_shortener/ShortUrlBaseUrlTest.java
@@ -5,20 +5,25 @@ import org.junit.jupiter.api.Test;
 import org.springframework.beans.factory.annotation.Autowired;
 import org.springframework.boot.test.context.SpringBootTest;
 import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
+import org.springframework.context.annotation.Import;
 import org.springframework.http.MediaType;
 import org.springframework.test.web.servlet.MockMvc;
 import org.springframework.test.web.servlet.MvcResult;
 
+import java.util.UUID;
+
 import static org.assertj.core.api.Assertions.assertThat;
 import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
 import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
 
 /**
  * shortUrl must be base-url + "/" + code even when app.base-url has no trailing slash.
- * HTTP only (MockMvc). Uses a URL that appears in no other test.
+ * HTTP only (MockMvc). Uses a URL that appears in no other test (with a per-run UUID,
+ * since links now persist in PostgreSQL).
  */
 @SpringBootTest(properties = "app.base-url=http://short.test")
 @AutoConfigureMockMvc
+@Import(TestcontainersConfiguration.class)
 class ShortUrlBaseUrlTest {
 
     @Autowired
@@ -28,7 +33,7 @@ class ShortUrlBaseUrlTest {
     void shortUrlIsBaseUrlSlashCodeWhenBaseUrlHasNoTrailingSlash() throws Exception {
         MvcResult result = mockMvc.perform(post("/api/urls")
                         .contentType(MediaType.APPLICATION_JSON)
-                        .content("{\"url\": \"https://baseurl-test.example.com/only-here\"}"))
+                        .content("{\"url\": \"https://baseurl-test.example.com/only-here?run=" + UUID.randomUUID() + "\"}"))
                 .andExpect(status().isCreated())
                 .andReturn();
 
diff --git a/src/test/java/com/malinda/url_shortener/UrlShortenerAcceptanceTest.java b/src/test/java/com/malinda/url_shortener/UrlShortenerAcceptanceTest.java
index 303cf70..1d3366c 100644
--- a/src/test/java/com/malinda/url_shortener/UrlShortenerAcceptanceTest.java
+++ b/src/test/java/com/malinda/url_shortener/UrlShortenerAcceptanceTest.java
@@ -5,10 +5,13 @@ import org.junit.jupiter.api.Test;
 import org.springframework.beans.factory.annotation.Autowired;
 import org.springframework.boot.test.context.SpringBootTest;
 import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
+import org.springframework.context.annotation.Import;
 import org.springframework.http.MediaType;
 import org.springframework.test.web.servlet.MockMvc;
 import org.springframework.test.web.servlet.MvcResult;
 
+import java.util.UUID;
+
 import static org.assertj.core.api.Assertions.assertThat;
 import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
 import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
@@ -20,14 +23,18 @@ import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.
  *   POST /api/urls {"url": "..."} -> 201 {"code": "...", "shortUrl": "<base>/<code>"}
  *   GET  /{code}                  -> 3xx, Location: original url | 404 if unknown
  * Exercises the app only through HTTP (MockMvc does not follow redirects).
+ * Stage 2: links persist in PostgreSQL (Testcontainers), so every URL carries a per-run
+ * UUID (RUN) to stay unique across test methods and classes sharing the database.
  */
 @SpringBootTest
 @AutoConfigureMockMvc
+@Import(TestcontainersConfiguration.class)
 class UrlShortenerAcceptanceTest {
 
+    private static final String RUN = UUID.randomUUID().toString();
     private static final String TRACKING_URL =
-            "https://shop.example.com/products/12345?utm_source=sms&utm_campaign=launch";
-    private static final String ENCODED_URL = "https://example.com/search?q=hello%20world";
+            "https://shop.example.com/products/12345?utm_source=sms&utm_campaign=launch&run=" + RUN;
+    private static final String ENCODED_URL = "https://example.com/search?q=hello%20world&run=" + RUN;
 
     @Autowired
     private MockMvc mockMvc;
@@ -56,7 +63,7 @@ class UrlShortenerAcceptanceTest {
 
     @Test
     void createReturns201WithCodeAndShortUrl() throws Exception {
-        shorten("https://example.com/some/page");
+        shorten("https://example.com/some/page?run=" + RUN);
     }
 
     @Test
@@ -79,8 +86,8 @@ class UrlShortenerAcceptanceTest {
 
     @Test
     void differentUrlsGetDifferentCodesAndRedirectToTheirOwnOriginal() throws Exception {
-        String urlA = "https://a.example.com/first";
-        String urlB = "https://b.example.com/second";
+        String urlA = "https://a.example.com/first?run=" + RUN;
+        String urlB = "https://b.example.com/second?run=" + RUN;
 
         String codeA = shorten(urlA);
         String codeB = shorten(urlB);
diff --git a/src/test/java/com/malinda/url_shortener/UrlShortenerApplicationTests.java b/src/test/java/com/malinda/url_shortener/UrlShortenerApplicationTests.java
index e757f3d..f505591 100644
--- a/src/test/java/com/malinda/url_shortener/UrlShortenerApplicationTests.java
+++ b/src/test/java/com/malinda/url_shortener/UrlShortenerApplicationTests.java
@@ -2,8 +2,10 @@ package com.malinda.url_shortener;
 
 import org.junit.jupiter.api.Test;
 import org.springframework.boot.test.context.SpringBootTest;
+import org.springframework.context.annotation.Import;
 
 @SpringBootTest
+@Import(TestcontainersConfiguration.class)
 class UrlShortenerApplicationTests {
 
 	@Test
diff --git a/src/test/java/com/malinda/url_shortener/UrlShortenerErrorCasesTest.java b/src/test/java/com/malinda/url_shortener/UrlShortenerErrorCasesTest.java
index 96c99fe..d260ce1 100644
--- a/src/test/java/com/malinda/url_shortener/UrlShortenerErrorCasesTest.java
+++ b/src/test/java/com/malinda/url_shortener/UrlShortenerErrorCasesTest.java
@@ -7,10 +7,13 @@ import org.junit.jupiter.params.provider.ValueSource;
 import org.springframework.beans.factory.annotation.Autowired;
 import org.springframework.boot.test.context.SpringBootTest;
 import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
+import org.springframework.context.annotation.Import;
 import org.springframework.http.MediaType;
 import org.springframework.test.web.servlet.MockMvc;
 import org.springframework.test.web.servlet.MvcResult;
 
+import java.util.UUID;
+
 import static org.assertj.core.api.Assertions.assertThat;
 import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
 import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@@ -20,11 +23,13 @@ import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.
  *   POST /api/urls new URL           -> 201 {"code", "shortUrl"}
  *   POST /api/urls already shortened -> 200 {"code", "shortUrl"} with the existing code
  *   POST /api/urls missing / blank / malformed / not http(s) -> 400
- * HTTP only (MockMvc). All test classes share one in-memory store, so every URL here
- * uses a host under errcases.example.com that appears in no other test.
+ * HTTP only (MockMvc). Test classes share one PostgreSQL (Testcontainers), so every URL here
+ * uses a host under errcases.example.com that appears in no other test, and the stored
+ * duplicate URL carries a per-run UUID.
  */
 @SpringBootTest
 @AutoConfigureMockMvc
+@Import(TestcontainersConfiguration.class)
 class UrlShortenerErrorCasesTest {
 
     @Autowired
@@ -39,7 +44,7 @@ class UrlShortenerErrorCasesTest {
 
     @Test
     void sameUrlTwiceReturns201ThenExisting200WithSameCodeAndShortUrl() throws Exception {
-        String body = "{\"url\": \"https://dup.errcases.example.com/page?x=1\"}";
+        String body = "{\"url\": \"https://dup.errcases.example.com/page?x=1&run=" + UUID.randomUUID() + "\"}";
 
         MvcResult first = mockMvc.perform(post("/api/urls")
                         .contentType(MediaType.APPLICATION_JSON)
```

### 6.4 New test files (full contents)

| Brief item | Test |
|---|---|
| 4a. Reads go to the database | `LinkPersistenceTest.redirect_readsRowInsertedDirectlyIntoDatabase` |
| 4b. The duplicate check goes to the database | `LinkPersistenceTest.duplicateCheck_findsRowInsertedDirectlyIntoDatabase_returns200WithThatCode` |
| 4c. Creates reach the database | `LinkPersistenceTest.create_writesExactlyOneRowWithCodeAndUrl` |
| 4d. A 300-character URL | `LinkPersistenceTest.url300CharactersLong_isStoredAndRedirectedUnchanged` |
| 4e. A code collision fails instead of overwriting (repository) | `CodeCollisionTest.savingNewLinkWithTakenCode_throwsDataIntegrityViolation_andKeepsOriginalUrl`. There is no `@Transactional`: each `saveAndFlush` commits in its own repository transaction, so the second write really reaches PostgreSQL |
| Extra: guard for "never the Compose database" | `TestDatabaseIsolationTest` (2 tests) |
| Extra: what a collision does over HTTP (to answer "which HTTP status") | `CodeCollisionHttpTest`. It inserts `(X, A)` with SQL, then stubs the static `ShortCodeGenerator.generateShortCode()` to return `X` with Mockito `mockStatic`. Mockito is already on the test classpath through `spring-boot-starter-webmvc-test`. Then it POSTs a new URL `B` and asserts that row `X` still holds `A` |

Directly inserted codes are 8 characters long (prefix `t`, `c` or `h` plus 7 hex digits). They fit `VARCHAR(10)` and can never equal the app's 6-character codes.

#### `TestcontainersConfiguration.java`
```java
package com.malinda.url_shortener;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Throwaway PostgreSQL for the test suite.
 *
 * @ServiceConnection makes Spring Boot point the DataSource (and therefore Flyway and JPA)
 * at this container instead of spring.datasource.* from application.yaml, so tests never
 * touch the Docker Compose database. Flyway creates the schema from db/migration.
 *
 * Image: postgres:18-alpine, the same image docker-compose.yaml uses.
 * Every @SpringBootTest class imports this configuration. The container is a bean, so it
 * lives as long as the (cached) application context that owns it.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    static final String POSTGRES_IMAGE = "postgres:18-alpine";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE)
                .asCompatibleSubstituteFor("postgres"));
    }
}
```

#### `TestDatabaseIsolationTest.java`
```java
package com.malinda.url_shortener;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guard: the suite talks to the Testcontainers PostgreSQL, never to the Compose database
 * (jdbc:postgresql://localhost:5432/shortener), and Flyway built the schema there.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TestDatabaseIsolationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PostgreSQLContainer postgres;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void dataSourcePointsAtTheTestContainerNotTheComposeDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            assertThat(url).isEqualTo(postgres.getJdbcUrl());
            assertThat(url).doesNotContain("localhost:5432/shortener");
        }
        String database = jdbc.sql("SELECT current_database()").query(String.class).single();
        assertThat(database).isEqualTo(postgres.getDatabaseName()).isNotEqualTo("shortener");
    }

    @Test
    void flywayCreatedTheSchemaInTheTestContainer() {
        Integer applied = jdbc.sql("SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success")
                .query(Integer.class).single();
        assertThat(applied).isEqualTo(1);

        Integer linksTable = jdbc.sql("SELECT count(*) FROM information_schema.tables WHERE table_name = 'links'")
                .query(Integer.class).single();
        assertThat(linksTable).isEqualTo(1);
    }
}
```

#### `LinkPersistenceTest.java`
```java
package com.malinda.url_shortener;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 2: reads, duplicate checks and creates go through PostgreSQL.
 * Rows are inserted and checked with plain SQL against the V1 schema:
 *   links(code VARCHAR(10) PRIMARY KEY, long_url TEXT NOT NULL)
 * Data persists across test methods in the shared context, so every URL and code is unique.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LinkPersistenceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    private static String uniqueUrl(String path) {
        return "https://persist.example.com/" + path + "/" + UUID.randomUUID();
    }

    /** 8 characters: never clashes with the app's 6-character codes, fits VARCHAR(10). */
    private static String uniqueCode() {
        return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 7);
    }

    private void insertRow(String code, String longUrl) {
        jdbc.sql("INSERT INTO links (code, long_url) VALUES (:code, :url)")
                .param("code", code)
                .param("url", longUrl)
                .update();
    }

    private MvcResult postUrl(String longUrl) throws Exception {
        return mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"" + longUrl + "\"}"))
                .andReturn();
    }

    @Test
    void redirect_readsRowInsertedDirectlyIntoDatabase() throws Exception {
        String code = uniqueCode();
        String longUrl = uniqueUrl("read");
        insertRow(code, longUrl);

        mockMvc.perform(get("/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", longUrl));
    }

    @Test
    void duplicateCheck_findsRowInsertedDirectlyIntoDatabase_returns200WithThatCode() throws Exception {
        String code = uniqueCode();
        String longUrl = uniqueUrl("dup");
        insertRow(code, longUrl);

        MvcResult result = postUrl(longUrl);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String json = result.getResponse().getContentAsString();
        assertThat((String) JsonPath.read(json, "$.code")).isEqualTo(code);
        assertThat((String) JsonPath.read(json, "$.shortUrl")).endsWith("/" + code);
    }

    @Test
    void create_writesExactlyOneRowWithCodeAndUrl() throws Exception {
        String longUrl = uniqueUrl("create");

        MvcResult result = postUrl(longUrl);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        String code = JsonPath.read(result.getResponse().getContentAsString(), "$.code");

        List<Map<String, Object>> rows = jdbc.sql("SELECT code, long_url FROM links WHERE code = :code")
                .param("code", code)
                .query()
                .listOfRows();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("code", code).containsEntry("long_url", longUrl);

        Integer rowsForUrl = jdbc.sql("SELECT count(*) FROM links WHERE long_url = :url")
                .param("url", longUrl)
                .query(Integer.class)
                .single();
        assertThat(rowsForUrl).isEqualTo(1);
    }

    @Test
    void url300CharactersLong_isStoredAndRedirectedUnchanged() throws Exception {
        String prefix = "https://long.example.com/" + UUID.randomUUID() + "/";
        String longUrl = prefix + "a".repeat(300 - prefix.length());
        assertThat(longUrl).hasSize(300);

        MvcResult result = postUrl(longUrl);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        String code = JsonPath.read(result.getResponse().getContentAsString(), "$.code");

        mockMvc.perform(get("/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", longUrl));
    }
}
```

#### `CodeCollisionTest.java`
```java
package com.malinda.url_shortener;

import com.malinda.url_shortener.entity.Link;
import com.malinda.url_shortener.repo.LinkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stage 2: saving a second, new Link under a code that is already taken must fail at the
 * database (primary key) instead of silently overwriting the first link's URL.
 *
 * No @Transactional on the class: each saveAndFlush runs in its own repository transaction
 * and commits, so the second insert really reaches PostgreSQL.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CodeCollisionTest {

    @Autowired
    private LinkRepository linkRepository;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void savingNewLinkWithTakenCode_throwsDataIntegrityViolation_andKeepsOriginalUrl() {
        String code = "c" + UUID.randomUUID().toString().replace("-", "").substring(0, 7);
        String urlA = "https://collision.example.com/A/" + UUID.randomUUID();
        String urlB = "https://collision.example.com/B/" + UUID.randomUUID();

        linkRepository.saveAndFlush(new Link(code, urlA));

        assertThatThrownBy(() -> linkRepository.saveAndFlush(new Link(code, urlB)))
                .isInstanceOf(DataIntegrityViolationException.class);

        String stored = jdbc.sql("SELECT long_url FROM links WHERE code = :code")
                .param("code", code)
                .query(String.class)
                .single();
        assertThat(stored).isEqualTo(urlA);
    }
}
```

#### `CodeCollisionHttpTest.java`
```java
package com.malinda.url_shortener;

import com.jayway.jsonpath.JsonPath;
import com.malinda.url_shortener.util.ShortCodeGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Stage 2: what the client sees when the generated code is already taken.
 *
 * A row (X, A) is inserted with SQL, then ShortCodeGenerator is stubbed (thread-local
 * static mock; MockMvc runs the request on this thread) to return X for a POST of a new
 * URL B. The existing link must not be overwritten, and code X must not be handed out
 * for B. The response status is printed so the report can record it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CodeCollisionHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void postWhoseGeneratedCodeIsTaken_doesNotOverwriteExistingLink() throws Exception {
        String takenCode = "h" + UUID.randomUUID().toString().replace("-", "").substring(0, 7);
        String urlA = "https://collision-http.example.com/A/" + UUID.randomUUID();
        String urlB = "https://collision-http.example.com/B/" + UUID.randomUUID();
        jdbc.sql("INSERT INTO links (code, long_url) VALUES (:code, :url)")
                .param("code", takenCode)
                .param("url", urlA)
                .update();

        MvcResult result;
        try (MockedStatic<ShortCodeGenerator> generator = mockStatic(ShortCodeGenerator.class)) {
            generator.when(ShortCodeGenerator::generateShortCode).thenReturn(takenCode);
            result = mockMvc.perform(post("/api/urls")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"url\": \"" + urlB + "\"}"))
                    .andReturn();
        }

        int status = result.getResponse().getStatus();
        String body = result.getResponse().getContentAsString();
        System.out.println("[CodeCollisionHttpTest] POST with taken code -> status " + status + ", body " + body);

        String stored = jdbc.sql("SELECT long_url FROM links WHERE code = :code")
                .param("code", takenCode)
                .query(String.class)
                .single();
        assertThat(stored).as("URL stored under the taken code after the POST").isEqualTo(urlA);

        if (status == 201) {
            assertThat((String) JsonPath.read(body, "$.code"))
                    .as("code handed out for the new URL")
                    .isNotEqualTo(takenCode);
        }
    }
}
```

#### Changed existing files, in full after the change

##### `UrlShortenerApplicationTests.java`
```java
package com.malinda.url_shortener;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UrlShortenerApplicationTests {

	@Test
	void contextLoads() {
	}

}
```

##### `UrlShortenerAcceptanceTest.java`
```java
package com.malinda.url_shortener;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Black-box acceptance test for the Stage 1 API contract:
 *   POST /api/urls {"url": "..."} -> 201 {"code": "...", "shortUrl": "<base>/<code>"}
 *   GET  /{code}                  -> 3xx, Location: original url | 404 if unknown
 * Exercises the app only through HTTP (MockMvc does not follow redirects).
 * Stage 2: links persist in PostgreSQL (Testcontainers), so every URL carries a per-run
 * UUID (RUN) to stay unique across test methods and classes sharing the database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UrlShortenerAcceptanceTest {

    private static final String RUN = UUID.randomUUID().toString();
    private static final String TRACKING_URL =
            "https://shop.example.com/products/12345?utm_source=sms&utm_campaign=launch&run=" + RUN;
    private static final String ENCODED_URL = "https://example.com/search?q=hello%20world&run=" + RUN;

    @Autowired
    private MockMvc mockMvc;

    /** POSTs a URL, asserts the creation contract, and returns the code. */
    private String shorten(String longUrl) throws Exception {
        String body = "{\"url\": " + jsonString(longUrl) + "}";
        MvcResult result = mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        String code = JsonPath.read(json, "$.code");
        String shortUrl = JsonPath.read(json, "$.shortUrl");

        assertThat(code).isNotBlank();
        assertThat(shortUrl).endsWith("/" + code);
        return code;
    }

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @Test
    void createReturns201WithCodeAndShortUrl() throws Exception {
        shorten("https://example.com/some/page?run=" + RUN);
    }

    @Test
    void shortCodeRedirectsToExactOriginalUrl() throws Exception {
        String code = shorten(TRACKING_URL);

        mockMvc.perform(get("/" + code))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", TRACKING_URL));
    }

    @Test
    void percentEncodedUrlIsNotDoubleEncoded() throws Exception {
        String code = shorten(ENCODED_URL);

        mockMvc.perform(get("/" + code))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", ENCODED_URL));
    }

    @Test
    void differentUrlsGetDifferentCodesAndRedirectToTheirOwnOriginal() throws Exception {
        String urlA = "https://a.example.com/first?run=" + RUN;
        String urlB = "https://b.example.com/second?run=" + RUN;

        String codeA = shorten(urlA);
        String codeB = shorten(urlB);

        assertThat(codeA).isNotEqualTo(codeB);

        mockMvc.perform(get("/" + codeA))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", urlA));
        mockMvc.perform(get("/" + codeB))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", urlB));
    }

    @Test
    void unknownCodeReturns404() throws Exception {
        mockMvc.perform(get("/neverCreatedCode123"))
                .andExpect(status().isNotFound());
    }
}
```

##### `UrlShortenerErrorCasesTest.java`
```java
package com.malinda.url_shortener;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Black-box error-case and duplicate tests for the Stage 1 API contract:
 *   POST /api/urls new URL           -> 201 {"code", "shortUrl"}
 *   POST /api/urls already shortened -> 200 {"code", "shortUrl"} with the existing code
 *   POST /api/urls missing / blank / malformed / not http(s) -> 400
 * HTTP only (MockMvc). Test classes share one PostgreSQL (Testcontainers), so every URL here
 * uses a host under errcases.example.com that appears in no other test, and the stored
 * duplicate URL carries a per-run UUID.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UrlShortenerErrorCasesTest {

    @Autowired
    private MockMvc mockMvc;

    private MvcResult postBody(String body) throws Exception {
        return mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    @Test
    void sameUrlTwiceReturns201ThenExisting200WithSameCodeAndShortUrl() throws Exception {
        String body = "{\"url\": \"https://dup.errcases.example.com/page?x=1&run=" + UUID.randomUUID() + "\"}";

        MvcResult first = mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        MvcResult second = mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        String firstJson = first.getResponse().getContentAsString();
        String secondJson = second.getResponse().getContentAsString();
        String firstCode = JsonPath.read(firstJson, "$.code");
        String firstShortUrl = JsonPath.read(firstJson, "$.shortUrl");

        assertThat(firstCode).isNotBlank();
        assertThat((String) JsonPath.read(secondJson, "$.code")).isEqualTo(firstCode);
        assertThat((String) JsonPath.read(secondJson, "$.shortUrl")).isEqualTo(firstShortUrl);
    }

    @ParameterizedTest(name = "[{index}] {0} -> 400")
    @ValueSource(strings = {
            "{}",
            "{\"url\": \"\"}",
            "{\"url\": \"   \"}",
            "{\"url\": \"not-a-url\"}",
            "{\"url\": \"https://\"}",
            "{\"url\": \"javascript:alert(1)\"}",
            "{\"url\": \"ftp://errcases.example.com/file\"}",
            "{\"url\": \"https://errcases.example.com/a b\"}"
    })
    void invalidInputReturns400(String body) throws Exception {
        assertThat(postBody(body).getResponse().getStatus()).isEqualTo(400);
    }
}
```

##### `ShortUrlBaseUrlTest.java`
```java
package com.malinda.url_shortener;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * shortUrl must be base-url + "/" + code even when app.base-url has no trailing slash.
 * HTTP only (MockMvc). Uses a URL that appears in no other test (with a per-run UUID,
 * since links now persist in PostgreSQL).
 */
@SpringBootTest(properties = "app.base-url=http://short.test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ShortUrlBaseUrlTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shortUrlIsBaseUrlSlashCodeWhenBaseUrlHasNoTrailingSlash() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://baseurl-test.example.com/only-here?run=" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        String code = JsonPath.read(json, "$.code");
        String shortUrl = JsonPath.read(json, "$.shortUrl");

        assertThat(code).isNotBlank();
        assertThat(shortUrl).isEqualTo("http://short.test/" + code);
    }
}
```

### 6.5 Commands and results

| Run | Command | Compose database | Result |
|---|---|---|---|
| 1 (first full run, before `CodeCollisionHttpTest` existed) | `./mvnw -B test` | **stopped** | `Tests run: 23, Failures: 1, Errors: 0, Skipped: 0` (`CodeCollisionTest`) |
| 2 (SQL evidence) | `./mvnw -B test -Dtest=CodeCollisionTest -Dlogging.level.org.hibernate.SQL=debug -Dlogging.level.org.hibernate.orm.jdbc.bind=trace` | stopped | 1 run, 1 failure (same assertion) |
| 3 (**final full run**, 20:16:34 to 20:18:40) | `./mvnw -B test` | running | **`Tests run: 24, Failures: 2, Errors: 0, Skipped: 0`, `BUILD FAILURE`**, 01:59 min |
| 4 (SQL evidence) | `./mvnw -B test -Dtest=CodeCollisionHttpTest -Dlogging.level.org.hibernate.SQL=debug -Dlogging.level.org.hibernate.orm.jdbc.bind=trace` | running | 1 run, 1 failure (same assertion) |

Logging levels were passed on the command line. No config file was edited.

**Per class (final run 3):**

| Test class | Tests | Passed | Failed | Result |
|---|---|---|---|---|
| `CodeCollisionHttpTest` (new) | 1 | 0 | 1 | **FAIL** |
| `CodeCollisionTest` (new) | 1 | 0 | 1 | **FAIL** |
| `LinkPersistenceTest` (new) | 4 | 4 | 0 | PASS |
| `TestDatabaseIsolationTest` (new) | 2 | 2 | 0 | PASS |
| `ShortUrlBaseUrlTest` | 1 | 1 | 0 | PASS |
| `UrlShortenerAcceptanceTest` | 5 | 5 | 0 | PASS |
| `UrlShortenerApplicationTests` | 1 | 1 | 0 | PASS |
| `UrlShortenerErrorCasesTest` | 9 | 9 | 0 | PASS |
| **Total** | **24** | **22** | **2** | **FAIL** |

The surefire summary from run 3:

```
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 38.19 s <<< FAILURE! -- in com.malinda.url_shortener.CodeCollisionHttpTest
[ERROR] com.malinda.url_shortener.CodeCollisionHttpTest.postWhoseGeneratedCodeIsTaken_doesNotOverwriteExistingLink -- Time elapsed: 7.008 s <<< FAILURE!
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 20.04 s <<< FAILURE! -- in com.malinda.url_shortener.CodeCollisionTest
[ERROR] com.malinda.url_shortener.CodeCollisionTest.savingNewLinkWithTakenCode_throwsDataIntegrityViolation_andKeepsOriginalUrl -- Time elapsed: 0.250 s <<< FAILURE!
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.382 s -- in com.malinda.url_shortener.LinkPersistenceTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 17.88 s -- in com.malinda.url_shortener.ShortUrlBaseUrlTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.447 s -- in com.malinda.url_shortener.TestDatabaseIsolationTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.582 s -- in com.malinda.url_shortener.UrlShortenerAcceptanceTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.098 s -- in com.malinda.url_shortener.UrlShortenerApplicationTests
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.853 s -- in com.malinda.url_shortener.UrlShortenerErrorCasesTest
[ERROR] Failures: 
[ERROR]   CodeCollisionHttpTest.postWhoseGeneratedCodeIsTaken_doesNotOverwriteExistingLink:68 [URL stored under the taken code after the POST] 
[ERROR]   CodeCollisionTest.savingNewLinkWithTakenCode_throwsDataIntegrityViolation_andKeepsOriginalUrl:42 
[ERROR] Tests run: 24, Failures: 2, Errors: 0, Skipped: 0
[ERROR] BUILD FAILURE
[INFO] Total time:  01:59 min
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-surefire-plugin:3.5.6:test (default-test) on project url-shortener: There are test failures.
```

**Full failure output** (`target/surefire-reports/*.txt`, run 3):

```
-------------------------------------------------------------------------------
Test set: com.malinda.url_shortener.CodeCollisionTest
-------------------------------------------------------------------------------
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 20.04 s <<< FAILURE! -- in com.malinda.url_shortener.CodeCollisionTest
com.malinda.url_shortener.CodeCollisionTest.savingNewLinkWithTakenCode_throwsDataIntegrityViolation_andKeepsOriginalUrl -- Time elapsed: 0.250 s <<< FAILURE!
java.lang.AssertionError: 

Expecting code to raise a throwable.
	at com.malinda.url_shortener.CodeCollisionTest.savingNewLinkWithTakenCode_throwsDataIntegrityViolation_andKeepsOriginalUrl(CodeCollisionTest.java:42)

-------------------------------------------------------------------------------
Test set: com.malinda.url_shortener.CodeCollisionHttpTest
-------------------------------------------------------------------------------
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 30.89 s <<< FAILURE! -- in com.malinda.url_shortener.CodeCollisionHttpTest
com.malinda.url_shortener.CodeCollisionHttpTest.postWhoseGeneratedCodeIsTaken_doesNotOverwriteExistingLink -- Time elapsed: 6.562 s <<< FAILURE!
org.opentest4j.AssertionFailedError: 
[URL stored under the taken code after the POST] 
expected: "https://collision-http.example.com/A/bcb47168-2d49-4b55-a8d5-ea88a25e2202"
 but was: "https://collision-http.example.com/B/95517c34-69ad-4ed8-b941-552c44d6aba0"
	at com.malinda.url_shortener.CodeCollisionHttpTest.postWhoseGeneratedCodeIsTaken_doesNotOverwriteExistingLink(CodeCollisionHttpTest.java:68)
```

- **`CodeCollisionTest` (line 42).** The second `saveAndFlush(new Link(code, urlB))` threw nothing.
- **`CodeCollisionHttpTest` (line 68).** After the POST, row `X` held URL `B` instead of `A`. The test also printed `[CodeCollisionHttpTest] POST with taken code -> status 201, body {"code":"h2904679","shortUrl":"http://localhost:8081/h2904679"}`. So the client got **201 with the taken code**. The second assertion (that a 201 must not reuse the taken code) never ran, because the first assertion failed before it.

**SQL for the repository collision (run 2):**

```
2026-11-22T20:07:42.928+05:30 org.hibernate.SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
2026-11-22T20:07:42.931+05:30 org.hibernate.orm.jdbc.bind: binding parameter (1:VARCHAR) <- [c9b0845e]
2026-11-22T20:07:43.042+05:30 org.hibernate.SQL: insert into links (long_url,code) values (?,?)
2026-11-22T20:07:43.045+05:30 org.hibernate.orm.jdbc.bind: binding parameter (1:VARCHAR) <- [https://collision.example.com/A/99e00920-a824-4c75-ade4-e1c22a5deb94]
2026-11-22T20:07:43.046+05:30 org.hibernate.orm.jdbc.bind: binding parameter (2:VARCHAR) <- [c9b0845e]
2026-11-22T20:07:43.217+05:30 org.hibernate.SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
2026-11-22T20:07:43.218+05:30 org.hibernate.orm.jdbc.bind: binding parameter (1:VARCHAR) <- [c9b0845e]
2026-11-22T20:07:43.241+05:30 org.hibernate.SQL: update links set long_url=? where code=?
2026-11-22T20:07:43.242+05:30 org.hibernate.orm.jdbc.bind: binding parameter (1:VARCHAR) <- [https://collision.example.com/B/85284324-9d9b-470c-ab2d-50086bb66b0e]
2026-11-22T20:07:43.243+05:30 org.hibernate.orm.jdbc.bind: binding parameter (2:VARCHAR) <- [c9b0845e]
```

**SQL for the HTTP collision (run 4):**

```
20:22:15.733 [main] INFO org.springframework.test.context.support.AnnotationConfigContextLoaderUtils -- Could not detect default configuration classes for test class [com.malinda.url_shortener.CodeCollisionHttpTest]: CodeCollisionHttpTest does not declare any static, non-private, non-final, nested classes annotated with @Configuration.
20:22:16.645 [main] INFO org.springframework.test.context.support.AnnotationConfigContextLoaderUtils -- Could not detect default configuration classes for test class [com.malinda.url_shortener.CodeCollisionHttpTest]: CodeCollisionHttpTest does not declare any static, non-private, non-final, nested classes annotated with @Configuration.
2026-11-22T20:22:44.597+05:30 org.hibernate.SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.long_url=?
2026-11-22T20:22:44.604+05:30 org.hibernate.orm.jdbc.bind: binding parameter (1:VARCHAR) <- [https://collision-http.example.com/B/95517c34-69ad-4ed8-b941-552c44d6aba0]
2026-11-22T20:22:45.070+05:30 org.hibernate.SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
2026-11-22T20:22:45.073+05:30 org.hibernate.orm.jdbc.bind: binding parameter (1:VARCHAR) <- [hab554ea]
2026-11-22T20:22:45.268+05:30 org.hibernate.SQL: update links set long_url=? where code=?
2026-11-22T20:22:45.275+05:30 org.hibernate.orm.jdbc.bind: binding parameter (1:VARCHAR) <- [https://collision-http.example.com/B/95517c34-69ad-4ed8-b941-552c44d6aba0]
2026-11-22T20:22:45.278+05:30 org.hibernate.orm.jdbc.bind: binding parameter (2:VARCHAR) <- [hab554ea]
[CodeCollisionHttpTest] POST with taken code -> status 201, body {"code":"hab554ea","shortUrl":"http://localhost:8081/hab554ea"}
```

## 7. Manual check transcripts

**Method.**
- Start Postgres: `docker compose up -d --wait`.
- Build once: `./mvnw -B -q -DskipTests package`, which produced `target/url-shortener-0.0.1-SNAPSHOT.jar`.
- Start the app in the background: `java -jar target/url-shortener-0.0.1-SNAPSHOT.jar --server.port=8090 --app.base-url=http://localhost:8090 --logging.level.org.hibernate.SQL=debug`.
- Run ID: `20261122200904`.
- Requests: `curl -s -i`, which does not follow redirects. The `Date`, `Connection`, `Keep-Alive`, `Transfer-Encoding` and `Vary` headers are trimmed.
- After each request, a helper reports what the app log gained during it: the `org.hibernate.SQL` lines, any WARN/ERROR/exception lines, and the number of stack-trace frames. `elapsed` is the wall-clock time of the curl call.

### 7.1 Startup (first start, PID 19132): Flyway and Hibernate lines and all WARN lines, verbatim

```
2026-11-22T20:09:18.486+05:30  INFO 19132 --- [url-shortener] [           main] org.flywaydb.core.FlywayExecutor         : Database: jdbc:postgresql://localhost:5432/shortener (PostgreSQL 18.6)
2026-11-22T20:09:18.955+05:30  INFO 19132 --- [url-shortener] [           main] o.f.core.internal.command.DbValidate     : Successfully validated 1 migration (execution time 00:00.161s)
2026-11-22T20:09:19.034+05:30  INFO 19132 --- [url-shortener] [           main] o.f.core.internal.command.DbMigrate      : Current version of schema "public": 1
2026-11-22T20:09:19.047+05:30  INFO 19132 --- [url-shortener] [           main] o.f.core.internal.command.DbMigrate      : Schema "public" is up to date. No migration necessary.
2026-11-22T20:09:19.496+05:30  INFO 19132 --- [url-shortener] [           main] org.hibernate.orm.jpa                    : HHH008540: Processing PersistenceUnitInfo [name: default]
2026-11-22T20:09:19.756+05:30  INFO 19132 --- [url-shortener] [           main] org.hibernate.orm.core                   : HHH000001: Hibernate ORM core version 7.4.13.Final
2026-11-22T20:09:21.887+05:30  INFO 19132 --- [url-shortener] [           main] org.hibernate.orm.connections.pooling    : HHH10001005: Database info:
	Database JDBC URL [jdbc:postgresql://localhost:5432/shortener]
	Database driver: PostgreSQL JDBC Driver
	Database dialect: PostgreSQLDialect
	Database version: 18.6
	Default catalog/schema: shortener/public
	Autocommit mode: undefined/unknown
	Isolation level: READ_COMMITTED [default READ_COMMITTED]
	JDBC fetch size: none
	Pool: DataSourceConnectionProvider
	Minimum pool size: undefined/unknown
	Maximum pool size: undefined/unknown
2026-11-22T20:09:24.769+05:30  INFO 19132 --- [url-shortener] [           main] org.hibernate.orm.core                   : HHH000489: No JTA platform available (set 'hibernate.transaction.jta.platform' to enable JTA platform integration)
2026-11-22T20:09:25.347+05:30  INFO 19132 --- [url-shortener] [           main] o.s.d.j.r.query.QueryEnhancerFactories   : Hibernate is in classpath; If applicable, HQL parser will be used.
2026-11-22T20:09:26.127+05:30  WARN 19132 --- [url-shortener] [           main] JpaBaseConfiguration$JpaWebConfiguration : spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering. Explicitly configure spring.jpa.open-in-view to disable this warning
```

The only WARN at startup is `spring.jpa.open-in-view`. The second start (PID 9896, after the crash and container recreate) logged the same Flyway sequence and the same single WARN:

```
2026-11-22T20:11:33.003+05:30  INFO 9896 --- [url-shortener] [           main] org.flywaydb.core.FlywayExecutor         : Database: jdbc:postgresql://localhost:5432/shortener (PostgreSQL 18.6)
2026-11-22T20:11:33.511+05:30  INFO 9896 --- [url-shortener] [           main] o.f.core.internal.command.DbValidate     : Successfully validated 1 migration (execution time 00:00.109s)
2026-11-22T20:11:33.589+05:30  INFO 9896 --- [url-shortener] [           main] o.f.core.internal.command.DbMigrate      : Current version of schema "public": 1
2026-11-22T20:11:33.602+05:30  INFO 9896 --- [url-shortener] [           main] o.f.core.internal.command.DbMigrate      : Schema "public" is up to date. No migration necessary.
2026-11-22T20:11:40.902+05:30  WARN 9896 --- [url-shortener] [           main] JpaBaseConfiguration$JpaWebConfiguration : spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering. Explicitly configure spring.jpa.open-in-view to disable this warning
```

### 7.2 Step 3.2: three new URLs, then GET each code

```
### 2. POST campaign/20261122200904/1 (new)
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{"url":"https://shop.example.com/campaign/20261122200904/1"}'
HTTP/1.1 201 
Content-Type: application/json

{"code":"xKP9u6","shortUrl":"http://localhost:8090/xKP9u6"}

[elapsed: 4.461s]
[SQL statements logged: 3]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.long_url=?
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
  SQL: insert into links (long_url,code) values (?,?)
[other log lines (WARN/ERROR/exception): none; stack-trace frames: 0]

### 2. POST campaign/20261122200904/2 (new)
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{"url":"https://shop.example.com/campaign/20261122200904/2"}'
HTTP/1.1 201 
Content-Type: application/json

{"code":"SIAh7r","shortUrl":"http://localhost:8090/SIAh7r"}

[elapsed: 0.657s]
[SQL statements logged: 3]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.long_url=?
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
  SQL: insert into links (long_url,code) values (?,?)
[other log lines (WARN/ERROR/exception): none; stack-trace frames: 0]

### 2. POST campaign/20261122200904/3 (new)
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{"url":"https://shop.example.com/campaign/20261122200904/3"}'
HTTP/1.1 201 
Content-Type: application/json

{"code":"pJmPdQ","shortUrl":"http://localhost:8090/pJmPdQ"}

[elapsed: 0.621s]
[SQL statements logged: 3]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.long_url=?
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
  SQL: insert into links (long_url,code) values (?,?)
[other log lines (WARN/ERROR/exception): none; stack-trace frames: 0]
```

```
### 2. GET xKP9u6
$ curl -s -i http://localhost:8090/xKP9u6
HTTP/1.1 302 
Location: https://shop.example.com/campaign/20261122200904/1
Content-Length: 0


[elapsed: 0.299s]
[SQL statements logged: 1]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
[other log lines (WARN/ERROR/exception): none; stack-trace frames: 0]

### 2. GET SIAh7r
$ curl -s -i http://localhost:8090/SIAh7r
HTTP/1.1 302 
Location: https://shop.example.com/campaign/20261122200904/2
Content-Length: 0


[elapsed: 0.211s]
[SQL statements logged: 1]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
[other log lines (WARN/ERROR/exception): none; stack-trace frames: 0]

### 2. GET pJmPdQ
$ curl -s -i http://localhost:8090/pJmPdQ
HTTP/1.1 302 
Location: https://shop.example.com/campaign/20261122200904/3
Content-Length: 0


[elapsed: 0.198s]
[SQL statements logged: 1]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
[other log lines (WARN/ERROR/exception): none; stack-trace frames: 0]

### 3b. POST campaign/20261122200904/1 again (existing)
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{"url":"https://shop.example.com/campaign/20261122200904/1"}'
HTTP/1.1 200 
Content-Type: application/json

{"code":"xKP9u6","shortUrl":"http://localhost:8090/xKP9u6"}

[elapsed: 0.229s]
[SQL statements logged: 1]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.long_url=?
[other log lines (WARN/ERROR/exception): none; stack-trace frames: 0]

### 3d. GET unknown code
$ curl -s -i http://localhost:8090/zzzzzz
HTTP/1.1 404 
Content-Type: application/json

{"message":"Unknown code"}

[elapsed: 0.254s]
[SQL statements logged: 1]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
[other log lines (WARN/ERROR/exception): none; stack-trace frames: 0]
```

| URL | POST | code | GET status | Location |
|---|---|---|---|---|
| `https://shop.example.com/campaign/20261122200904/1` | 201 | `xKP9u6` | 302 | `https://shop.example.com/campaign/20261122200904/1` |
| `https://shop.example.com/campaign/20261122200904/2` | 201 | `SIAh7r` | 302 | `https://shop.example.com/campaign/20261122200904/2` |
| `https://shop.example.com/campaign/20261122200904/3` | 201 | `pJmPdQ` | 302 | `https://shop.example.com/campaign/20261122200904/3` |

### 7.3 Step 3.3: SQL per request

These SQL lines are copied verbatim from the app log (`org.hibernate.SQL` at DEBUG) of the first start:

```
(a) first POST of a new URL (campaign/.../1), 3 queries
2026-11-22T20:09:51.596+05:30 DEBUG 19132 --- [url-shortener] [nio-8090-exec-1] org.hibernate.SQL                        : select l1_0.code,l1_0.long_url from links l1_0 where l1_0.long_url=?
2026-11-22T20:09:51.816+05:30 DEBUG 19132 --- [url-shortener] [nio-8090-exec-1] org.hibernate.SQL                        : select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
2026-11-22T20:09:51.934+05:30 DEBUG 19132 --- [url-shortener] [nio-8090-exec-1] org.hibernate.SQL                        : insert into links (long_url,code) values (?,?)

(b) POST of a URL that already exists (campaign/.../1 again), 1 query
2026-11-22T20:10:14.667+05:30 DEBUG 19132 --- [url-shortener] [nio-8090-exec-1] org.hibernate.SQL                        : select l1_0.code,l1_0.long_url from links l1_0 where l1_0.long_url=?

(c) GET of an existing code (xKP9u6), 1 query
2026-11-22T20:10:09.264+05:30 DEBUG 19132 --- [url-shortener] [nio-8090-exec-5] org.hibernate.SQL                        : select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?

(d) GET of an unknown code (zzzzzz), 1 query
2026-11-22T20:10:16.504+05:30 DEBUG 19132 --- [url-shortener] [nio-8090-exec-3] org.hibernate.SQL                        : select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
```

| Request | Queries | Statements |
|---|---|---|
| (a) POST, new URL | **3** | select by `long_url`, **select by `code`**, insert |
| (b) POST, existing URL | 1 | select by `long_url` |
| (c) GET, existing code | 1 | select by `code` (primary key) |
| (d) GET, unknown code | 1 | select by `code` (primary key) |

The same counts held for every other request of the same kind, in both app runs (sections 7.2, 7.5, 7.6 and 12).

### 7.4 Step 3.4: crash, then recreate the database container

```
$ taskkill /PID 19132 /F
SUCCESS: The process with PID 19132 has been terminated.
$ tasklist /FI "IMAGENAME eq java.exe"

Image Name                     PID Session Name        Session#    Mem Usage
========================= ======== ================ =========== ============
java.exe                     21080 Console                    1      8,880 K
java.exe                     22268 Console                    1      7,500 K
java.exe                     16876 Console                    1      5,132 K
$ netstat -ano | findstr :8090
  TCP    [::1]:61533            [::1]:8090             TIME_WAIT       0
  TCP    [::1]:61537            [::1]:8090             TIME_WAIT       0
  TCP    [::1]:61538            [::1]:8090             TIME_WAIT       0
  TCP    [::1]:61541            [::1]:8090             TIME_WAIT       0
  TCP    [::1]:61543            [::1]:8090             TIME_WAIT       0
  TCP    [::1]:61545            [::1]:8090             TIME_WAIT       0
  TCP    [::1]:61547            [::1]:8090             TIME_WAIT       0
  TCP    [::1]:61550            [::1]:8090             TIME_WAIT       0

$ docker ps --no-trunc (before)
ID=5c43552a85fd1afe7a61f1c77322b8ca46084dbc19a9ae32340b78784f232c48 status=Up 2 minutes (healthy)
$ docker compose down
 Container url-shortener-postgres-1  Stopping
 Container url-shortener-postgres-1  Stopped
 Container url-shortener-postgres-1  Removing
 Container url-shortener-postgres-1  Removed
 Network url-shortener_default  Removing
 Network url-shortener_default  Removed
(no postgres container exists)
DRIVER    VOLUME NAME
local     url-shortener_pgdata
$ docker compose up -d --wait
 Network url-shortener_default  Creating
 Network url-shortener_default  Created
 Container url-shortener-postgres-1  Creating
 Container url-shortener-postgres-1  Created
 Container url-shortener-postgres-1  Starting
 Container url-shortener-postgres-1  Started
 Container url-shortener-postgres-1  Waiting
 Container url-shortener-postgres-1  Healthy
$ docker ps --no-trunc (after)
ID=35c6806b3f26c07174959f976a30c2c1e6917bbccb7fa1a232b4a43e4aba16de status=Up 6 seconds (healthy) created=2026-11-22 20:11:02 +0530 +0530
```

- **Hard kill.** `taskkill /F` ended PID 19132. The app log simply stops after the last SQL line: there are no shutdown lines.
- **No app process left.** Nothing is `LISTENING` on 8090; only client-side `TIME_WAIT` entries remain. The three `java.exe` processes still listed are the pre-existing IntelliJ JVMs (21080, 22268, 16876; see section 3), not this app.
- **Container IDs.** Before: `5c43552a85fd1afe7a61f1c77322b8ca46084dbc19a9ae32340b78784f232c48`. After: `35c6806b3f26c07174959f976a30c2c1e6917bbccb7fa1a232b4a43e4aba16de`. The container was removed and recreated, and the `url-shortener_pgdata` volume was kept.
- **Restart.** The app was started again with the same command (PID 9896). Its startup lines are in section 7.1.

### 7.5 Step 3.5: after the crash and recreate

```
### 5. GET xKP9u6 (after crash + container recreate)
$ curl -s -i http://localhost:8090/xKP9u6
HTTP/1.1 302 
Location: https://shop.example.com/campaign/20261122200904/1
Content-Length: 0


[elapsed: 2.496s]
[SQL statements logged: 1]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]

### 5. GET SIAh7r (after crash + container recreate)
$ curl -s -i http://localhost:8090/SIAh7r
HTTP/1.1 302 
Location: https://shop.example.com/campaign/20261122200904/2
Content-Length: 0


[elapsed: 0.278s]
[SQL statements logged: 1]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]

### 5. GET pJmPdQ (after crash + container recreate)
$ curl -s -i http://localhost:8090/pJmPdQ
HTTP/1.1 302 
Location: https://shop.example.com/campaign/20261122200904/3
Content-Length: 0


[elapsed: 0.325s]
[SQL statements logged: 1]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]

### 5. POST campaign/20261122200904/1 again (after restart)
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{"url":"https://shop.example.com/campaign/20261122200904/1"}'
HTTP/1.1 200 
Content-Type: application/json

{"code":"xKP9u6","shortUrl":"http://localhost:8090/xKP9u6"}

[elapsed: 0.329s]
[SQL statements logged: 1]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.long_url=?
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]
```

| code | status | Location |
|---|---|---|
| `xKP9u6` | 302 | `https://shop.example.com/campaign/20261122200904/1` |
| `SIAh7r` | 302 | `https://shop.example.com/campaign/20261122200904/2` |
| `pJmPdQ` | 302 | `https://shop.example.com/campaign/20261122200904/3` |

Re-POSTing `campaign/20261122200904/1` returned **200 with code `xKP9u6`**, which **matches** the code from step 3.2. For comparison, in the Stage 1 in-memory version (`docs/reviews/stage-2-restart-repro.md`, 2026-11-12), all three codes returned 404 after a restart, and the re-POST returned 201 with a new code.

### 7.6 Step 3.6: validation is unchanged

```
== Step 3.6 validation
### 6a. blank url
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{"url": "   "}'
HTTP/1.1 400 
Content-Type: application/json

{"message":"URL cannot be empty"}

[elapsed: 0.332s]
[SQL statements logged: 0]
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]

### 6b. missing url ({})
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{}'
HTTP/1.1 400 
Content-Type: application/json

{"message":"URL cannot be empty"}

[elapsed: 0.181s]
[SQL statements logged: 0]
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]

### 6c. invalid URL (not-a-url)
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{"url": "not-a-url"}'
HTTP/1.1 400 
Content-Type: application/json

{"message":"Invalid URL format"}

[elapsed: 0.206s]
[SQL statements logged: 0]
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]

### 6d. unknown code
$ curl -s -i http://localhost:8090/neverCreated
HTTP/1.1 404 
Content-Type: application/json

{"message":"Unknown code"}

[elapsed: 0.493s]
[SQL statements logged: 1]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]

== Step 5: Stage 1 contract cases
### S1-1a. create new
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{"url":"https://curl.example.com/articles/42?ref=manual&run=20261122200904"}'
HTTP/1.1 201 
Content-Type: application/json

{"code":"QoQt8u","shortUrl":"http://localhost:8090/QoQt8u"}

[elapsed: 2.125s]
[SQL statements logged: 3]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.long_url=?
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
  SQL: insert into links (long_url,code) values (?,?)
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]
```

| Case | Status | Body | Stack trace in log |
|---|---|---|---|
| Blank url (`"   "`) | 400 | `{"message":"URL cannot be empty"}` | No (nothing logged, 0 SQL) |
| Missing url (`{}`) | 400 | `{"message":"URL cannot be empty"}` | No (nothing logged, 0 SQL) |
| Invalid URL (`not-a-url`) | 400 | `{"message":"Invalid URL format"}` | No (nothing logged, 0 SQL) |
| Unknown code (`/neverCreated`) | 404 | `{"message":"Unknown code"}` | No (1 SQL select, nothing else logged) |

Invalid input is rejected before any database access (0 SQL statements).

### 7.7 Step 3.7: database down

This extra request, made just before the database-down check, gives the 300-character URL that the psql query shows:

```
### Extra: 300-character URL
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{"url":"https://shop.example.com/campaign/20261122200904/long/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}'
HTTP/1.1 201 
Content-Type: application/json

{"code":"y8MtqX","shortUrl":"http://localhost:8090/y8MtqX"}

[elapsed: 0.431s]
[SQL statements logged: 3]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.long_url=?
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
  SQL: insert into links (long_url,code) values (?,?)
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]

### Extra: follow 300-char code y8MtqX
$ curl -s -i http://localhost:8090/y8MtqX
HTTP/1.1 302 
Location: https://shop.example.com/campaign/20261122200904/long/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
Content-Length: 0


[elapsed: 0.105s]
[SQL statements logged: 1]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]
```

With the app running, I stopped PostgreSQL and requested a known code:

```
$ docker compose stop postgres
 Container url-shortener-postgres-1  Stopping
 Container url-shortener-postgres-1  Stopped
status=Exited (0) 1 second ago
### 7a. GET xKP9u6 with PostgreSQL stopped
$ curl -s -i http://localhost:8090/xKP9u6 --max-time 120
HTTP/1.1 500 
Content-Type: application/json

{"timestamp":"2026-11-22T14:44:16.623Z","status":500,"error":"Internal Server Error","path":"/xKP9u6"}

[elapsed: 30.542s]
[SQL statements logged: 0]
[WARN/ERROR/exception log lines: yes; stack-trace frames: 24]
2026-11-22T20:13:46.494+05:30  WARN 9896 --- [url-shortener] [nio-8090-exec-9] com.zaxxer.hikari.pool.PoolBase          : HikariPool-1 - Failed to validate connection org.postgresql.jdbc.PgConnection@2c1d57bc (This connection has been closed.). Possibly consider using a shorter maxLifetime value.
2026-11-22T20:13:46.511+05:30  WARN 9896 --- [url-shortener] [nio-8090-exec-9] com.zaxxer.hikari.pool.PoolBase          : HikariPool-1 - Failed to validate connection org.postgresql.jdbc.PgConnection@38f30fa9 (This connection has been closed.). Possibly consider using a shorter maxLifetime value.
2026-11-22T20:13:46.514+05:30  WARN 9896 --- [url-shortener] [nio-8090-exec-9] com.zaxxer.hikari.pool.PoolBase          : HikariPool-1 - Failed to validate connection org.postgresql.jdbc.PgConnection@3f337cac (This connection has been closed.). Possibly consider using a shorter maxLifetime value.
2026-11-22T20:13:46.516+05:30  WARN 9896 --- [url-shortener] [nio-8090-exec-9] com.zaxxer.hikari.pool.PoolBase          : HikariPool-1 - Failed to validate connection org.postgresql.jdbc.PgConnection@1d62d857 (This connection has been closed.). Possibly consider using a shorter maxLifetime value.
2026-11-22T20:13:46.518+05:30  WARN 9896 --- [url-shortener] [nio-8090-exec-9] com.zaxxer.hikari.pool.PoolBase          : HikariPool-1 - Failed to validate connection org.postgresql.jdbc.PgConnection@15afe4e0 (This connection has been closed.). Possibly consider using a shorter maxLifetime value.
2026-11-22T20:13:46.520+05:30  WARN 9896 --- [url-shortener] [nio-8090-exec-9] com.zaxxer.hikari.pool.PoolBase          : HikariPool-1 - Failed to validate connection org.postgresql.jdbc.PgConnection@35012fbb (This connection has been closed.). Possibly consider using a shorter maxLifetime value.
2026-11-22T20:13:46.523+05:30  WARN 9896 --- [url-shortener] [nio-8090-exec-9] com.zaxxer.hikari.pool.PoolBase          : HikariPool-1 - Failed to validate connection org.postgresql.jdbc.PgConnection@11f3e3ab (This connection has been closed.). Possibly consider using a shorter maxLifetime value.
2026-11-22T20:13:46.525+05:30  WARN 9896 --- [url-shortener] [nio-8090-exec-9] com.zaxxer.hikari.pool.PoolBase          : HikariPool-1 - Failed to validate connection org.postgresql.jdbc.PgConnection@1a5821a2 (This connection has been closed.). Possibly consider using a shorter maxLifetime value.
```

App log for that request (the Hikari validation warnings, Hibernate's errors and the servlet ERROR are verbatim; after the first 6 frames the stack trace is trimmed, and the frame count is given at the end):

```
2026-11-22T20:13:46.494+05:30  WARN 9896 --- [url-shortener] [nio-8090-exec-9] com.zaxxer.hikari.pool.PoolBase          : HikariPool-1 - Failed to validate connection org.postgresql.jdbc.PgConnection@2c1d57bc (This connection has been closed.). Possibly consider using a shorter maxLifetime value.
2026-11-22T20:13:46.511+05:30  WARN 9896 --- [url-shortener] [nio-8090-exec-9] com.zaxxer.hikari.pool.PoolBase          : HikariPool-1 - Failed to validate connection org.postgresql.jdbc.PgConnection@38f30fa9 (This connection has been closed.). Possibly consider using a shorter maxLifetime value.
2026-11-22T20:13:46.514+05:30  WARN 9896 --- [url-shortener] [nio-8090-exec-9] com.zaxxer.hikari.pool.PoolBase          : HikariPool-1 - Failed to validate connection org.postgresql.jdbc.PgConnection@3f337cac (This connection has been closed.). Possibly consider using a shorter maxLifetime value.
2026-11-22T20:13:46.516+05:30  WARN 9896 --- [url-shortener] [nio-8090-exec-9] com.zaxxer.hikari.pool.PoolBase          : HikariPool-1 - Failed to validate connection org.postgresql.jdbc.PgConnection@1d62d857 (This connection has been closed.). Possibly consider using a shorter maxLifetime value.
2026-11-22T20:13:46.518+05:30  WARN 9896 --- [url-shortener] [nio-8090-exec-9] com.zaxxer.hikari.pool.PoolBase          : HikariPool-1 - Failed to validate connection org.postgresql.jdbc.PgConnection@15afe4e0 (This connection has been closed.). Possibly consider using a shorter maxLifetime value.
2026-11-22T20:13:46.520+05:30  WARN 9896 --- [url-shortener] [nio-8090-exec-9] com.zaxxer.hikari.pool.PoolBase          : HikariPool-1 - Failed to validate connection org.postgresql.jdbc.PgConnection@35012fbb (This connection has been closed.). Possibly consider using a shorter maxLifetime value.
2026-11-22T20:13:46.523+05:30  WARN 9896 --- [url-shortener] [nio-8090-exec-9] com.zaxxer.hikari.pool.PoolBase          : HikariPool-1 - Failed to validate connection org.postgresql.jdbc.PgConnection@11f3e3ab (This connection has been closed.). Possibly consider using a shorter maxLifetime value.
2026-11-22T20:13:46.525+05:30  WARN 9896 --- [url-shortener] [nio-8090-exec-9] com.zaxxer.hikari.pool.PoolBase          : HikariPool-1 - Failed to validate connection org.postgresql.jdbc.PgConnection@1a5821a2 (This connection has been closed.). Possibly consider using a shorter maxLifetime value.
2026-11-22T20:13:46.528+05:30  WARN 9896 --- [url-shortener] [nio-8090-exec-9] com.zaxxer.hikari.pool.PoolBase          : HikariPool-1 - Failed to validate connection org.postgresql.jdbc.PgConnection@70ba4eed (This connection has been closed.). Possibly consider using a shorter maxLifetime value.
2026-11-22T20:13:46.531+05:30  WARN 9896 --- [url-shortener] [nio-8090-exec-9] com.zaxxer.hikari.pool.PoolBase          : HikariPool-1 - Failed to validate connection org.postgresql.jdbc.PgConnection@4cfacbb6 (This connection has been closed.). Possibly consider using a shorter maxLifetime value.
2026-11-22T20:14:16.492+05:30  WARN 9896 --- [url-shortener] [nio-8090-exec-9] org.hibernate.orm.jdbc.error             : HHH000247: ErrorCode: 0, SQLState: 08001
2026-11-22T20:14:16.497+05:30  WARN 9896 --- [url-shortener] [nio-8090-exec-9] org.hibernate.orm.jdbc.error             : HikariPool-1 - Connection is not available, request timed out after 30031ms (total=0, active=0, idle=0, waiting=0)
2026-11-22T20:14:16.497+05:30  WARN 9896 --- [url-shortener] [nio-8090-exec-9] org.hibernate.orm.jdbc.error             : HHH000247: ErrorCode: 0, SQLState: 08001
2026-11-22T20:14:16.497+05:30  WARN 9896 --- [url-shortener] [nio-8090-exec-9] org.hibernate.orm.jdbc.error             : HikariPool-1 - Connection is not available, request timed out after 30031ms (total=0, active=0, idle=0, waiting=0)
2026-11-22T20:14:16.574+05:30 ERROR 9896 --- [url-shortener] [nio-8090-exec-9] o.a.c.c.C.[.[.[/].[dispatcherServlet]    : Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception [Request processing failed: org.springframework.dao.DataAccessResourceFailureException: Unable to acquire JDBC Connection [HikariPool-1 - Connection is not available, request timed out after 30031ms (total=0, active=0, idle=0, waiting=0)] [n/a]] with root cause

java.net.ConnectException: Connection refused: getsockopt
	at java.base/sun.nio.ch.Net.pollConnect(Native Method) ~[na:na]
	at java.base/sun.nio.ch.Net.pollConnectNow(Net.java:682) ~[na:na]
	at java.base/sun.nio.ch.NioSocketImpl.timedFinishConnect(NioSocketImpl.java:542) ~[na:na]
	at java.base/sun.nio.ch.NioSocketImpl.connect(NioSocketImpl.java:592) ~[na:na]
	at java.base/java.net.SocksSocketImpl.connect(SocksSocketImpl.java:327) ~[na:na]
	at java.base/java.net.Socket.connect(Socket.java:751) ~[na:na]
	... (remaining frames trimmed; see count below)

[stack-trace frames ('at ...' lines) logged for this one request: 24]
```

- **With PostgreSQL stopped:** **500**, body `{"timestamp":"2026-11-22T14:44:16.623Z","status":500,"error":"Internal Server Error","path":"/xKP9u6"}`, after **30.5 s** (curl: 30.542 s; Hikari: `request timed out after 30031ms`).
- **What the log shows:** 10 `Failed to validate connection` WARN lines (the pool throwing away all 10 dead connections), 4 Hibernate WARN lines (SQLState `08001`), and 1 ERROR with a 24-frame stack trace. The root cause is `java.net.ConnectException: Connection refused`.

Then I started PostgreSQL again and repeated the request:

```
$ docker compose start postgres
 Container url-shortener-postgres-1  Starting
 Container url-shortener-postgres-1  Started
$ docker compose up -d --wait   (wait for healthy)
 Container url-shortener-postgres-1  Running
 Container url-shortener-postgres-1  Waiting
 Container url-shortener-postgres-1  Healthy
ID=35c6806b3f26 status=Up 9 seconds (healthy)
### 7b. GET xKP9u6 after PostgreSQL is healthy again
$ curl -s -i http://localhost:8090/xKP9u6
HTTP/1.1 302 
Location: https://shop.example.com/campaign/20261122200904/1
Content-Length: 0


[elapsed: 0.523s]
[SQL statements logged: 1]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]
```

**After PostgreSQL was healthy again:** 302, same `Location`, in 0.52 s. The app recovered on its own, without a restart.

### 7.8 Step 3.9: stop the app

- `taskkill /PID 9896` without `/F` was refused ("This process can only be terminated forcefully"), so I used `taskkill /PID 9896 /F`.
- Nothing listens on 8090 or 8091 any more, and PostgreSQL is left running (container `35c6806b3f26`, healthy).
- The extra instance used for Stage 1 case 6b (port 8091, section 12) was also stopped.

## 8. psql output

```
$ docker compose exec postgres psql -U shortener -d shortener -c "\d links"
                       Table "public.links"
  Column  |         Type          | Collation | Nullable | Default 
----------+-----------------------+-----------+----------+---------
 code     | character varying(10) |           | not null | 
 long_url | text                  |           | not null | 
Indexes:
    "links_pkey" PRIMARY KEY, btree (code)

$ docker compose exec postgres psql -U shortener -d shortener -c "SELECT * FROM flyway_schema_history;"
 installed_rank | version | description  | type |        script        |  checksum   | installed_by |        installed_on        | execution_time | success 
----------------+---------+--------------+------+----------------------+-------------+--------------+----------------------------+----------------+---------
              1 | 1       | create links | SQL  | V1__create_links.sql | -1165468445 | shortener    | 2026-11-22 14:18:42.183361 |             43 | t
(1 row)

$ docker compose exec postgres psql -U shortener -d shortener -c "SELECT code, length(url) FROM links WHERE url LIKE '%20261122200904%';"   (as written in the brief)
ERROR:  column "url" does not exist
LINE 1: SELECT code, length(url) FROM links WHERE url LIKE '%2026112...
                            ^

$ docker compose exec postgres psql -U shortener -d shortener -c "SELECT code, length(long_url) FROM links WHERE long_url LIKE '%20261122200904%';"   (column name from V1)
  code  | length 
--------+--------
 hCYfcv |     49
 SIAh7r |     50
 pJmPdQ |     50
 xKP9u6 |     50
 ihUjCT |     61
 QoQt8u |     66
 tZUXiE |     67
 y8MtqX |    300
(8 rows)

$ ... -c "SELECT count(*) FROM links;"
 count 
-------
    10
(1 row)
```

- **`\d links`** matches V1. `code` is `character varying(10)`, not null, and is the primary key (`links_pkey`, btree). `long_url` is `text`, not null. There is no other index.
- **`flyway_schema_history`** has one successful row for V1, with checksum `-1165468445`. That is the checksum of the current file. The row was installed on 2026-11-22 14:18:42 UTC (19:48 local), after the checksum-mismatch error seen earlier the same day (V1 applied while empty, with checksum 0). That is consistent with the schema history having been reset and V1 reapplied; I did not see how it was done.
- **The brief's query** (`length(url)`, `url LIKE`) fails, because the column is `long_url`.
- **With `long_url`**, the query finds 8 rows for this run: the 3 campaign links, the Stage 1 contract probes, and the 300-character URL (`y8MtqX`, length 300). `hCYfcv` is 49 characters because `é` counts as one character. The extra trailing-slash instance (section 12) wrote to the same database after this query, so there is one more row now than shown here.

## 9. Review findings by severity

Order: correctness, then concurrency/data integrity, then performance, then design, then style. Each finding is tagged **[observed]** (reproduced by a test or a manual check) or **[code reading]**. Line numbers refer to section 5.

### 9.1 Correctness

**C1. A code collision silently overwrites an existing link, and the client gets 201.** [observed]
- *Where:* `entity/Link.java` lines 16-21 (assigned `@Id private String code`, with no `Persistable` and no `@Version`), together with `UrlServiceIMPL.createShortUrl` line 49 (`linkRepository.save(link)`).
- *What:*
  - Spring Data's `SimpleJpaRepository.save` decides between `persist` and `merge` using `JpaEntityInformation.isNew(entity)`. For an entity without `@Version` and with a non-primitive id, "new" means "the id is null".
  - `Link` always has its code set before `save`, so Spring Data treats it as **not new** and calls `EntityManager.merge`. Merge first loads the row by primary key (the `select ... where code=?` seen on every create).
  - If no row exists, merge inserts. If one does exist, merge copies the new state onto it and Hibernate issues `update links set long_url=? where code=?`.
  - The primary-key constraint is never exercised, so no `DataIntegrityViolationException` is thrown.
- *Evidence:*
  - `CodeCollisionTest` fails ("Expecting code to raise a throwable"), and its SQL shows `select`, then `update` on the second save.
  - `CodeCollisionHttpTest` fails. Over HTTP the POST returns **201 `{"code":"<taken code>"}`**, and the stored URL for that code changes from `A` to `B`.
- *Why it matters:* Codes come from 6 random characters with no uniqueness check. When a collision happens, an existing short link that people already share starts redirecting to someone else's URL. It does so silently: nothing is logged and nothing fails. In effect a random new user can take over an existing link. Stage 1 had the same overwrite with the map's `put`. Stage 2 was the chance to let the primary key prevent it, and the current mapping bypasses it.
- *Fix direction:* Make inserting a new link a real `persist`, so a duplicate key reaches the database as a constraint violation. Options are making the entity report its new-ness to Spring Data (for example, `Persistable` with a transient "new" flag), or inserting through an API that never merges. Then decide what a collision should do (for example, generate a new code and retry a bounded number of times). Test it with the two collision tests here.

**C2. Errors from the persistence layer reach the client as a generic 500 with a full stack trace.** [observed for a database outage; code reading for `DataIntegrityViolationException`]
- *Where:* `GlobalExceptionHandler` (handles only `InvalidUrlException` and `InvalidCodeException`).
- *What:*
  - With the database down, `DataAccessResourceFailureException` propagated to the servlet container. The result was 500 with Spring's default body, plus an ERROR line with a 24-frame stack trace (section 7.7).
  - Once C1 is fixed, a collision will raise `DataIntegrityViolationException` from `save`. With no handler and no retry, that also becomes a 500 with a stack trace. Today a collision gives 201 (C1), so this path cannot happen yet.
- *Why it matters:* The client cannot tell "try again" apart from "this request is broken". Retryable infrastructure failures look like server bugs (500 rather than, say, 503). And each failure writes a large stack trace to the log.
- *Fix direction:* Decide the status for each persistence failure class: unavailable or timeout versus integrity violation. Map them deliberately in the exception handler and keep the error body format consistent (see D3, carried over from Stage 1). Retry behaviour is parked (section 11).

### 9.2 Concurrency / data integrity

**I1. The duplicate check and the insert are not atomic, and nothing in the schema enforces "one code per URL".** [code reading] (the symptom is parked: section 11)
- *Where:* `UrlServiceIMPL.createShortUrl` lines 34 and 47-49; `V1__create_links.sql` (no unique constraint on `long_url`).
- *What (facts):*
  - There is no `@Transactional` anywhere in `src/main`.
  - `findByLongUrl` (line 34) runs in its own read-only repository transaction, and `save` (line 49) runs in a separate read-write one. `SimpleJpaRepository` is `@Transactional(readOnly = true)` at class level and `@Transactional` on `save`.
  - With `open-in-view` on, both calls share one `EntityManager` for the request, but not one transaction.
  - Even a single transaction at READ COMMITTED (the isolation level the startup log reports) would not stop two requests from both seeing "no row" and both inserting.
- *Why it matters:* Two concurrent POSTs of the same new URL can create two rows with different codes. After that, every later POST of that URL runs `findByLongUrl`, whose `Optional` return type expects at most one row. Spring Data would then throw `IncorrectResultSizeDataAccessException` (500) for that URL from then on [code reading].
- *Fix direction:* Let the database enforce the invariant you want (a unique constraint on the URL, or a deliberate decision that duplicates are allowed). Then handle the resulting violation explicitly: re-read and return the existing code with 200. That belongs to the stage that addresses concurrency.

**I2. The entity mapping is looser than the table, and `ddl-auto: validate` does not catch the difference.** [code reading]
- *Where:* `Link.java` lines 17-21 versus `V1__create_links.sql`.
- *What:*
  - Column names and types line up, and Hibernate's `validate` passed at every startup: `code` maps to `varchar`, and `long_url` maps to `text`, which is accepted for `String`.
  - The entity does not say that `code` has length 10, or that either column is non-null. Hibernate's schema validation checks only that tables and columns exist and that their types are compatible, not lengths or nullability.
  - The `code` field has no `@Column`, so its column name comes from the field name by convention.
- *Why it matters:* The entity no longer documents the constraints the database enforces, and a future change could drift (for example, a longer code) without startup catching it. The database still enforces the constraints, so this is low severity.
- *Fix direction:* State the column constraints on the mapping so that it mirrors V1 (`length`, `nullable`, and an explicit column name for `code`).

### 9.3 Performance

**P1. Every create runs an extra primary-key SELECT before the INSERT.** [observed]
- *Where:* Same cause as C1 (`save` turns into `merge`).
- *What:* A new create runs 3 statements (select by `long_url`, select by `code`, insert) instead of 2 (section 7.3).
- *Why it matters:* It is one extra round trip per write. In a write-heavy load test this shows up directly as latency and database load. More importantly, it is a symptom of C1.
- *Fix direction:* The fix for C1 removes it. Then confirm that the SQL log shows `select by long_url` followed by `insert` only.

**P2. `spring.jpa.open-in-view` is on by default.** [observed: startup WARN]
- *Where:* `application.yaml` does not set it, and Boot logs `JpaBaseConfiguration$JpaWebConfiguration : spring.jpa.open-in-view is enabled by default`.
- *What:* An `EntityManager` is bound to every web request for its whole duration, including JSON serialisation and the redirect. The app has no lazy associations that need it.
- *Why it matters:* Under load, a request can keep a database connection for longer than its queries need. It also hides where the transaction boundaries really are (see I1). Connection-pool sizing is parked (section 11).
- *Fix direction:* Decide on it explicitly in configuration rather than taking the default. For this app, turning it off would cost nothing.

(The full scan behind `findByLongUrl` is parked: section 11.)

### 9.4 Design

**D1 (new). `findByCode` duplicates the inherited `findById`.** [code reading]
- *Where:* `LinkRepository` line 12, used at `UrlServiceIMPL` line 61.
- *What:* `code` is the `@Id`, so `findById(code)` already exists. The derived `findByCode` generates a JPQL query that bypasses the persistence-context lookup by id. The SQL is the same (section 7.3).
- *Why it matters:* There are two ways to do the same lookup, and the custom one doesn't say that it is the primary key.
- *Fix direction:* Use the inherited method and drop the custom one.

**D2 (new). `@Repository` on a Spring Data interface is redundant.** [code reading]
- *Where:* `LinkRepository` line 9.
- *What and why:* Spring Data detects `JpaRepository` sub-interfaces and applies exception translation by itself, so the annotation adds nothing.
- *Fix direction:* Remove it, or keep it deliberately as documentation.

**D3 (carried over from Stage 1, D3). Error responses have several shapes.** [observed]
- `{"message"}` is used for validation errors and unknown codes.
- Spring's default `{"timestamp","status","error","path"}` is used for an empty body, malformed JSON, `GET /`, and now **every 500** (database down).

**D4 (carried over from Stage 1, last future-stage flag). Code generation is a static call.** [observed]
- *Where:* `ShortCodeGenerator.generateShortCode()` (static), called at `UrlServiceIMPL` line 47.
- *What and why:* To reproduce a collision over HTTP, the test had to replace the static method with Mockito `mockStatic` (`CodeCollisionHttpTest`). The collision fix (C1) and its retry logic will need tests like this, so controllable generation will matter.
- *Fix direction:* Make the generator a dependency the service receives, so tests can provide a deterministic one.

**Carried over from Stage 1 and still open (unchanged):**
- D1 (the service builds the response DTO and `shortUrl`)
- D2 (`@Value` field injection for `baseUrl`, now at line 23)
- D4 (`redirectToOriginalUrl` naming)
- D5 (base-URL normalisation on every call)

### 9.5 Style

- **S1 (new; a regression from Stage 1's "no unused imports").** [code reading] `UrlServiceIMPL` line 14 `import java.util.Map;` and line 16 `import java.util.concurrent.ConcurrentHashMap;` are unused. They are left over from the in-memory store.
- **S2 (new).** [code reading] `UrlServiceIMPL` lines 36-45: the `if (existingLink.isPresent())` block is indented one level deeper than the surrounding code. Line 48 `new Link(code,longUrl)` is missing a space after the comma. `Link.java` has no newline at the end of the file.
- **S3 (new).** [code reading] Lombok `@AllArgsConstructor` and `@NoArgsConstructor` on the entity give it a public no-argument constructor, which exists only for JPA. JPA requires only protected visibility, so it could be narrowed. This is minor.
- **Carried over from Stage 1, still open:** S1 (the wildcard import in `UrlController` line 9), S2 (double spaces in `UrlController` lines 38 and 40 and `GlobalExceptionHandler` line 21), S3 (`IMPL` naming), S4 (the magic number 6 in `ShortCodeGenerator` line 14), and S5 (naming nits: `InvalidCodeException` means "not found", `schema` should be `scheme`, `response` variables).

### 9.6 Specific checks requested

**No in-memory link store remains in `src/main`.** [code reading] I searched `src/main/java` for `Map`, `HashMap`, `ConcurrentHashMap`, `List<`, `Set<`, `Collection` and `static`. Results:
- `Map` and `ConcurrentHashMap` appear only as the two unused **imports** in `UrlServiceIMPL` (S1). No field, local variable or static holds a collection.
- The only fields in the service are `final LinkRepository linkRepository` and `String baseUrl`.
- Static state exists only in `ShortCodeGenerator` (`static final String CHARACTERS` and `static final SecureRandom secureRandom`), which is not link storage.
- **Behavioural proof:** codes created before the crash resolve after it (section 7.5), and rows inserted directly with SQL are found by both the GET path and the duplicate path (`LinkPersistenceTest` 4a and 4b).

**The entity mapping matches the V1 migration.** [code reading, plus startup validation]

| V1 column | Entity field | Mapping | Match |
|---|---|---|---|
| `code VARCHAR(10) PRIMARY KEY` | `String code` | `@Id`, no `@Column`, so the name comes from the field | Name and type yes. Length and non-null are not declared (I2) |
| `long_url TEXT NOT NULL` | `String longUrl` | `@Column(name = "long_url")` | Name and type yes. Non-null is not declared (I2) |

The table is `@Table(name = "links")`. `ddl-auto: validate` passed at every startup (no schema error in any log).

**How the entity tells Spring Data that it is new.** It doesn't:
- `Link` doesn't implement `Persistable`, and has no `@Version` field and no generated id.
- So Spring Data falls back to "new if and only if the id is null". The id is always set before `save`, so every `save` is a `merge`.
- **The SQL from Step 3.3 confirms this:** each new create shows `select ... where l1_0.code=?` immediately before `insert into links`. The collision test shows the same select followed by `update links set long_url=? where code=?`.

**jakarta and Spring annotations used in `src/main`.**
- `jakarta.persistence`: `@Entity`, `@Table`, `@Id`, `@Column` (all in `Link`). No `jakarta.validation` annotations are used.
- Spring: `@SpringBootApplication`, `@RestController`, `@PostMapping`, `@GetMapping`, `@RequestBody`, `@PathVariable` (the latter four through the wildcard import), `@RestControllerAdvice`, `@ExceptionHandler`, `@Service`, `@Value`, `@Repository`.
- Lombok: `@AllArgsConstructor`, `@NoArgsConstructor`, `@Getter`.
- Also `@Override`.
- There is no `@Transactional` anywhere.

**Transaction boundaries in the service (facts).**
- `UrlServiceIMPL` and `UrlService` declare no transactions.
- `createShortUrl`:
  1. `findByLongUrl` runs in a read-only transaction that the repository proxy opens and commits.
  2. If no row is found, `save` runs in a separate read-write transaction. Inside it, `merge` issues `select by code`, and the `insert` (or `update`) is flushed at that transaction's commit.
- `redirectToOriginalUrl`: `findByCode` runs in one read-only repository transaction.
- Because of open-in-view, all calls in one request share one persistence context, but each repository call has its own transaction.

**How a `DataIntegrityViolationException` from a collision reaches the client.**
- Today it **doesn't occur**: a code collision gives **201** with the taken code, and the existing row is overwritten (`CodeCollisionHttpTest`, C1).
- If it did occur (after fixing C1, or from a concurrent insert of the same code), nothing in `src/main` catches it: no service catch, no handler. Spring MVC's default handling would then return **500** with the default error body and log a stack trace. That is the same path the observed `DataAccessResourceFailureException` took in section 7.7 [code reading].

**The API contract, status codes, code generation and duplicate behaviour are unchanged from Stage 1.** [observed]
- `POST /api/urls` returns 201 for a new URL and 200 with the same code for a duplicate. Invalid input gives 400 with the same messages. `GET /{code}` gives 302 with the exact `Location`, and an unknown code gives 404 `{"message":"Unknown code"}`.
- Codes are 6 characters from `[0-9a-zA-Z]` (`ShortCodeGenerator` is unchanged).
- The full case-by-case comparison is in section 12.

## 10. What's done well

- **The schema is owned by a migration, and Hibernate only validates it** (`ddl-auto: validate`). Hibernate never changes the schema, and a mapping that doesn't match the table stops the app at startup instead of failing at runtime.
- **The short code is the primary key.** A redirect is one indexed primary-key lookup (1 query per GET, observed), which is the hot path of a URL shortener.
- **The duplicate path costs one query** and still returns 200 with the same code. It also works across restarts (section 7.5), which fixes the main Stage 1 limitation shown in `stage-2-restart-repro.md`.
- **The in-memory store is gone completely**, not left in place next to the database: the service has no state of its own.
- **Validation still runs before any database access.** Invalid input costs 0 SQL statements (section 7.6), and every 4xx case still logs no stack trace.
- **`long_url` is `TEXT`**, so long URLs need no arbitrary length limit. A 300-character URL round-trips unchanged.
- **The Compose setup is solid.**
  - It pins the major version (`postgres:18-alpine`) and uses a named volume at the PostgreSQL 18 mount path (`/var/lib/postgresql`).
  - It binds the port to `127.0.0.1` only and has a `pg_isready` healthcheck, so `--wait` works.
  - Its comments explain `down` versus `down -v`.
  - The data survived container removal and recreation.
- **It recovers without a restart.** After the database came back, the next request succeeded with no app restart (section 7.7).
- **Small, focused commits** (Flyway config, entity and repository, service refactor). The schema history now holds a single clean V1 row whose checksum matches the file.
- **Constructor injection of the repository**, and the controller is unchanged from Stage 1, so the contract cannot have drifted at the web layer.

## 11. Parked items and future-stage flags (symptoms only)

- **Searching by URL without an index:** every POST runs `select ... where long_url=?`, and `\d links` shows only `links_pkey`. That is a sequential scan, which grows with the table.
- **Two simultaneous POSTs of the same new URL:** the check and the insert are separate transactions, with no unique constraint on `long_url`. Both POSTs can get 201 with different codes. Later POSTs of that URL would then match 2 rows for a single-result query (I1).
- **Random-code collisions as the table grows:** a collision currently overwrites an existing link and returns 201 (C1). The chance of a collision grows with the number of stored links (62^6 codes, no retry).
- **Caching:** every redirect, including repeats of the same code, runs a database query (1 select per GET).
- **The database slow or down:** each request blocks for Hikari's default 30 s connection timeout, then returns 500 with a stack trace. There is no retry, no fail-fast and no 503.
- **Connection-pool sizing:** pool settings are Hikari defaults (nothing configured). Open-in-view holds an `EntityManager` per request. When the database stopped, 10 pooled connections were discarded on the first request.
- **Credentials in `application.yaml`:** `username: shortener` and `password: shortener` are committed in plain text, along with `docker-compose.yaml`.
- **Other future-stage flags:**
  - **No input normalisation:** `.../42/?ref=...` and `.../42?ref=...` get different codes (S1-X3, unchanged).
  - **Non-ASCII `Location`:** the redirect percent-encodes it, as in Stage 1's C1 (S1-X2b, unchanged).
  - **The error-body format of 5xx responses** is Spring's default (D3).
  - **Migration discipline:** V1 was edited after it had already been applied once (the earlier checksum mismatch). Applied migrations should be immutable from now on.
  - **No `stage-1` tag:** the Stage 1 baseline is not tagged in git (section 3), so "unchanged from Stage 1" can only be checked against the review document, not against a git ref.
  - **Unrelated to the code:** `CLAUDE.md` mentions `load-tests/`, but the folder is `load-test/`.

## 12. Comparison with Stage 1

I reran every contract case in `docs/reviews/stage-1-final-review.md` (sections 7.1 to 7.5) against the Stage 2 jar (`--app.base-url=http://localhost:8090`), with a run suffix added to each stored URL so that it would be new. Case 6b needs a base URL with a trailing slash, so it ran on a second short-lived instance (`--server.port=8091 --app.base-url=http://localhost:8091/`).

```
== Step 3.6 validation
### 6a. blank url
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{"url": "   "}'
HTTP/1.1 400 
Content-Type: application/json

{"message":"URL cannot be empty"}

[elapsed: 0.332s]
[SQL statements logged: 0]
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]

### 6b. missing url ({})
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{}'
HTTP/1.1 400 
Content-Type: application/json

{"message":"URL cannot be empty"}

[elapsed: 0.181s]
[SQL statements logged: 0]
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]

### 6c. invalid URL (not-a-url)
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{"url": "not-a-url"}'
HTTP/1.1 400 
Content-Type: application/json

{"message":"Invalid URL format"}

[elapsed: 0.206s]
[SQL statements logged: 0]
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]

### 6d. unknown code
$ curl -s -i http://localhost:8090/neverCreated
HTTP/1.1 404 
Content-Type: application/json

{"message":"Unknown code"}

[elapsed: 0.493s]
[SQL statements logged: 1]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]

== Step 5: Stage 1 contract cases
### S1-1a. create new
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{"url":"https://curl.example.com/articles/42?ref=manual&run=20261122200904"}'
HTTP/1.1 201 
Content-Type: application/json

{"code":"QoQt8u","shortUrl":"http://localhost:8090/QoQt8u"}

[elapsed: 2.125s]
[SQL statements logged: 3]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.long_url=?
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
  SQL: insert into links (long_url,code) values (?,?)
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]
```

```
### S1-1b. follow QoQt8u
$ curl -s -i http://localhost:8090/QoQt8u
HTTP/1.1 302 
Location: https://curl.example.com/articles/42?ref=manual&run=20261122200904
Content-Length: 0


[elapsed: 0.420s]
[SQL statements logged: 1]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]

### S1-2. same URL again (expect 200, QoQt8u)
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{"url":"https://curl.example.com/articles/42?ref=manual&run=20261122200904"}'
HTTP/1.1 200 
Content-Type: application/json

{"code":"QoQt8u","shortUrl":"http://localhost:8090/QoQt8u"}

[elapsed: 0.163s]
[SQL statements logged: 1]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.long_url=?
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]

### S1-3a. percent-encoded URL
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{"url":"https://example.com/search?q=hello%20world&run=20261122200904"}'
HTTP/1.1 201 
Content-Type: application/json

{"code":"ihUjCT","shortUrl":"http://localhost:8090/ihUjCT"}

[elapsed: 0.636s]
[SQL statements logged: 3]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.long_url=?
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
  SQL: insert into links (long_url,code) values (?,?)
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]
```

```
### S1-3b. follow ihUjCT
$ curl -s -i http://localhost:8090/ihUjCT
HTTP/1.1 302 
Location: https://example.com/search?q=hello%20world&run=20261122200904
Content-Length: 0


[elapsed: 0.173s]
[SQL statements logged: 1]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]

### S1-4. unknown code
$ curl -s -i http://localhost:8090/zzzzzz
HTTP/1.1 404 
Content-Type: application/json

{"message":"Unknown code"}

[elapsed: 0.153s]
[SQL statements logged: 1]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]

### S1-5a. empty body (no content)
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json'
HTTP/1.1 400 
Content-Type: application/json

{"timestamp":"2026-11-22T14:42:50.475Z","status":400,"error":"Bad Request","path":"/api/urls"}

[elapsed: 0.387s]
[SQL statements logged: 0]
[WARN/ERROR/exception log lines: yes; stack-trace frames: 0]
2026-11-22T20:12:50.345+05:30  WARN 9896 --- [url-shortener] [nio-8090-exec-1] .w.s.m.s.DefaultHandlerExceptionResolver : Resolved [org.springframework.http.converter.HttpMessageNotReadableException: Required request body is missing: public org.springframework.http.ResponseEntity<com.malinda.url_shortener.dto.CreateUrlResponse> com.malinda.url_shortener.controller.UrlController.createShortUrl(com.

### S1-5b. {}
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{}'
HTTP/1.1 400 
Content-Type: application/json

{"message":"URL cannot be empty"}

[elapsed: 0.336s]
[SQL statements logged: 0]
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]

### S1-5c. empty string
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{"url": ""}'
HTTP/1.1 400 
Content-Type: application/json

{"message":"URL cannot be empty"}

[elapsed: 0.255s]
[SQL statements logged: 0]
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]

### S1-5d. blank
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{"url": "   "}'
HTTP/1.1 400 
Content-Type: application/json

{"message":"URL cannot be empty"}

[elapsed: 0.240s]
[SQL statements logged: 0]
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]

### S1-5e. not-a-url
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{"url": "not-a-url"}'
HTTP/1.1 400 
Content-Type: application/json

{"message":"Invalid URL format"}

[elapsed: 0.162s]
[SQL statements logged: 0]
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]

### S1-5f. https:// (no host)
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{"url": "https://"}'
HTTP/1.1 400 
Content-Type: application/json

{"message":"Malformed URL"}

[elapsed: 0.314s]
[SQL statements logged: 0]
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]

### S1-5g. javascript scheme
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{"url": "javascript:alert(1)"}'
HTTP/1.1 400 
Content-Type: application/json

{"message":"Invalid URL format"}

[elapsed: 0.440s]
[SQL statements logged: 0]
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]

### S1-5h. ftp scheme
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{"url": "ftp://errcases.example.com/file"}'
HTTP/1.1 400 
Content-Type: application/json

{"message":"Invalid URL format"}

[elapsed: 1.255s]
[SQL statements logged: 0]
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]

### S1-5i. space in path
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{"url": "https://errcases.example.com/a b"}'
HTTP/1.1 400 
Content-Type: application/json

{"message":"Malformed URL"}

[elapsed: 0.509s]
[SQL statements logged: 0]
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]

### S1-X1. malformed JSON
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{"url":'
HTTP/1.1 400 
Content-Type: application/json

{"timestamp":"2026-11-22T14:43:09.281Z","status":400,"error":"Bad Request","path":"/api/urls"}

[elapsed: 0.191s]
[SQL statements logged: 0]
[WARN/ERROR/exception log lines: yes; stack-trace frames: 0]
2026-11-22T20:13:09.270+05:30  WARN 9896 --- [url-shortener] [io-8090-exec-10] .w.s.m.s.DefaultHandlerExceptionResolver : Resolved [org.springframework.http.converter.HttpMessageNotReadableException: JSON parse error: Unexpected end-of-input within/between Object entries]

### S1-X2. non-ASCII path, UTF-8 body file: {"url":"https://probe.example.com/café?run=20261122200904"}
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' --data-binary @C:/Users/malin/AppData/Local/Temp/claude/f0a14b620a0a888f/eba3dac7-fd73-4638-b769-c46877a43022/scratchpad/body-cafe.json
HTTP/1.1 201 
Content-Type: application/json

{"code":"hCYfcv","shortUrl":"http://localhost:8090/hCYfcv"}

[elapsed: 0.354s]
[SQL statements logged: 3]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.long_url=?
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
  SQL: insert into links (long_url,code) values (?,?)
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]
```

```
### S1-X2b. follow hCYfcv (stored URL contains é)
$ curl -s -i http://localhost:8090/hCYfcv
HTTP/1.1 302 
Location: https://probe.example.com/caf%C3%A9?run=20261122200904
Content-Length: 0


[elapsed: 0.222s]
[SQL statements logged: 1]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]

### S1-X3. trailing-slash variant of S1-1a's URL
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{"url":"https://curl.example.com/articles/42/?ref=manual&run=20261122200904"}'
HTTP/1.1 201 
Content-Type: application/json

{"code":"tZUXiE","shortUrl":"http://localhost:8090/tZUXiE"}

[elapsed: 0.477s]
[SQL statements logged: 3]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.long_url=?
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
  SQL: insert into links (long_url,code) values (?,?)
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]

### S1-7.5 GET / (no code)
$ curl -s -i http://localhost:8090/
HTTP/1.1 404 
Content-Type: application/json

{"timestamp":"2026-11-22T14:43:26.954Z","status":404,"error":"Not Found","path":"/"}

[elapsed: 0.352s]
[SQL statements logged: 0]
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]
```

```
### S1-6b. create with --app.base-url=http://localhost:8091/ (trailing slash)
$ curl -s -i -X POST http://localhost:8091/api/urls -H 'Content-Type: application/json' -d '{"url":"https://baseurl.example.com/page?run=20261122200904"}'
HTTP/1.1 201 
Content-Type: application/json

{"code":"xVc7cW","shortUrl":"http://localhost:8091/xVc7cW"}

[elapsed: 4.489s]
[SQL statements logged: 0]
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]

### S1-6b. follow xVc7cW
$ curl -s -i http://localhost:8091/xVc7cW
HTTP/1.1 302 
Location: https://baseurl.example.com/page?run=20261122200904
Content-Length: 0


[elapsed: 0.366s]
[SQL statements logged: 0]
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]
```

| Stage 1 case | Stage 1 result | Stage 2 result | Same? |
|---|---|---|---|
| 1a create new | 201 `{"code","shortUrl"}` | 201 `{"code":"QoQt8u","shortUrl":"http://localhost:8090/QoQt8u"}` | Yes |
| 1b follow | 302, exact `Location` | 302, `Location: https://curl.example.com/articles/42?ref=manual&run=20261122200904` | Yes |
| 2 same URL again | 200, same code | 200, `QoQt8u` | Yes |
| 3a percent-encoded URL | 201 | 201 `ihUjCT` | Yes |
| 3b follow | 302, `q=hello%20world`, no double-encoding | 302, `...?q=hello%20world&run=...` | Yes |
| 4 unknown code | 404 `{"message":"Unknown code"}`, nothing logged | 404 `{"message":"Unknown code"}`, nothing logged except 1 SQL | Yes |
| 5a empty body | 400, Spring default body, 1 WARN | 400, Spring default body, 1 WARN (`Required request body is missing`) | Yes |
| 5b `{}` | 400 `URL cannot be empty` | 400 `URL cannot be empty` | Yes |
| 5c `""` | 400 `URL cannot be empty` | 400 `URL cannot be empty` | Yes |
| 5d `"   "` | 400 `URL cannot be empty` | 400 `URL cannot be empty` | Yes |
| 5e `not-a-url` | 400 `Invalid URL format` | 400 `Invalid URL format` | Yes |
| 5f `https://` | 400 `Malformed URL` | 400 `Malformed URL` | Yes |
| 5g `javascript:alert(1)` | 400 `Invalid URL format` | 400 `Invalid URL format` | Yes |
| 5h `ftp://...` | 400 `Invalid URL format` | 400 `Invalid URL format` | Yes |
| 5i space in path | 400 `Malformed URL` | 400 `Malformed URL` | Yes |
| X1 malformed JSON | 400, Spring default body, 1 WARN | 400, Spring default body, 1 WARN (`JSON parse error`) | Yes |
| X2 non-ASCII URL (UTF-8 body file) | 201 | 201 `hCYfcv` | Yes |
| X2b follow | 302, `Location: .../caf%C3%A9` | 302, `Location: https://probe.example.com/caf%C3%A9?run=...` | Yes (same Stage 1 C1 behaviour) |
| X3 trailing-slash variant | 201, new code | 201 `tZUXiE` (differs from `QoQt8u`) | Yes |
| 6a base URL without a trailing slash | `http://localhost:18082/<code>` | `http://localhost:8090/<code>` (every response above) | Yes |
| 6b base URL with a trailing slash | `http://localhost:18083/<code>`, no `//` | `http://localhost:8091/xVc7cW`, then 302 | Yes |
| 7.4 no stack traces for 4xx | 0 frames | 0 frames for every 4xx above | Yes |
| 7.5 `GET /` | 404, Spring default body | 404, Spring default body | Yes |

**Differences: none in the contract.** Behaviour outside the Stage 1 contract has changed:
- **Durability.** Links now survive restarts (section 7.5).
- **New failure mode.** The database being unavailable gives a 30 s hang and then a 500 with a stack trace (section 7.7). Stage 1 could not fail this way.
- **Collision handling.** It is the same as Stage 1 (the new mapping replaces the old one silently), but it now happens through a SQL `UPDATE` (C1).
- **Test count.** The Stage 1 suite had 16 tests. All 16 still exist with the same assertions and still pass; the suite now has 24.

## 13. Facts only

**The `links` table definition.** From `V1__create_links.sql` and `\d links`:

```
CREATE TABLE links(
    code VARCHAR(10) PRIMARY KEY,
    long_url TEXT NOT NULL
);
-- \d links: code character varying(10) not null; long_url text not null;
--           index "links_pkey" PRIMARY KEY, btree (code); no other indexes or constraints
```

**How the entity signals that it is new.** It does not.
- `Link` has an assigned `String` `@Id`, does not implement `Persistable`, and has no `@Version`.
- Spring Data therefore treats an entity as new only if its id is `null`. `UrlServiceIMPL` always sets the code before `save`, so every `save` takes the `merge` path.

**Queries run per request** (observed, `org.hibernate.SQL`):

| Request | Statements, in order |
|---|---|
| Create, new URL | `select l1_0.code,l1_0.long_url from links l1_0 where l1_0.long_url=?`; `select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?`; `insert into links (long_url,code) values (?,?)` (3) |
| Create, duplicate URL | `select l1_0.code,l1_0.long_url from links l1_0 where l1_0.long_url=?` (1) |
| Create, new URL whose generated code is taken | `select ... where l1_0.long_url=?`; `select ... where l1_0.code=?`; `update links set long_url=? where code=?` (3) |
| Redirect, known code | `select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?` (1) |
| Redirect, unknown code | `select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?` (1), then 404 |
| Invalid input (400) | none (0) |

**The HTTP status a code collision produces.** **201 Created**. The body is `{"code":"<the taken code>","shortUrl":"<base>/<the taken code>"}`, and the existing row's `long_url` is overwritten with the new URL. No exception is thrown and nothing is logged (`CodeCollisionHttpTest`).

**What happens to a request while PostgreSQL is down, and how long it takes.**
- `GET /xKP9u6` blocked for **30.5 s** (curl 30.542 s), the length of Hikari's default `connectionTimeout`.
- It then returned **500** with Spring's default body `{"timestamp":...,"status":500,"error":"Internal Server Error","path":"/xKP9u6"}`.
- The log got 10 Hikari `Failed to validate connection` WARN lines, 4 Hibernate `SQLState: 08001` WARN lines, and an ERROR for `DataAccessResourceFailureException: Unable to acquire JDBC Connection` with root cause `java.net.ConnectException: Connection refused` and 24 stack-trace frames.
- No retry happened.
- After `docker compose start postgres` and a healthy check, the same request returned 302 in 0.52 s, without restarting the app.

## Authorship

- **This session.**
  - I did **not** create, edit or delete any file under `src/main`: no Java, no `application.yaml`, no `db/migration`.
  - I did not edit `docker-compose.yaml`, `load-test/` or `CLAUDE.md`, and I did not commit, stage or push.
  - `git diff --stat -- src/main` is empty.
  - Earlier in this session I explained the Flyway checksum-mismatch error to the author, without editing any file.
- **Files I changed:**
  - `pom.xml`: three test-scoped dependencies (section 6.1).
  - Four existing test files (section 6.3).
- **Files I created:**
  - Five test files (section 6.4).
  - This report and its PDF.
  - Build output went to `target/`, and scratch scripts and logs went to a session temp directory outside the repository.
- **State I changed outside the repository:**
  - I stopped, removed and recreated the Compose PostgreSQL container as the brief requires. The `pgdata` volume was kept, and PostgreSQL is running at the end.
  - The manual checks added rows to the Compose database (all URLs contain `20261122200904` or `campaign/20261122200904`).
  - Testcontainers created and removed its own throwaway containers.
- **Everything under `src/main`** is the author's work (all commits are by `malinda-sampath`).
