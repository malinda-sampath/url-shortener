# URL Shortener: Stage 2 Review, revision 2 (verification of fixes)

| | |
|---|---|
| Project | `com.malinda:url-shortener` |
| Code reviewed | Branch `dev`, HEAD `246399d` "Open Session in view false". The `src/main` diff is taken against `2cebc34`, the commit the first review covered |
| Review date | 2026-11-22 (local time UTC+05:30). Tests 23:11 to 23:15, manual checks 23:16 to 23:18 (run ID `20261122231551`) |
| Reviewer | Claude Code (Claude Opus 5.5), at the author's request |
| Compared against | `docs/reviews/stage-2-review.md` (first Stage 2 review) |

## 1. Summary

**All 8 Stage 2 exit criteria now pass, and so do all 24 tests.** `Link` now implements `Persistable` with a transient "new" flag, so `save()` runs a real `persist`. A create runs 2 statements (`select by long_url`, `insert`), not 3, and a code collision reaches the primary key as `23505 duplicate key ... "links_pkey"` instead of silently overwriting the row (C1 and P1 fixed).

**Open-in-view is off** (P2 fixed; the startup now logs **0 WARN lines**). `findById` replaces `findByCode` (D1), the unused imports are gone (S1), and the JPA constructor is protected (S3).

**The main open item is C2.** A collision now fails the request with an unhandled `DataIntegrityViolationException`: no retry and no handler, so in the test it escapes `MockMvc.perform()`. In the running app that means a 500 with a stack trace (by code reading). I2, D2, D3, D4 and S2 are still open, and I1 stays parked.

I did not create, edit or delete anything under `src/main`. The only file I changed is `src/test/.../CodeCollisionHttpTest.java` (section 5.1).

## 2. Stage 2 exit checklist

| Criterion | Result | Evidence |
|---|---|---|
| All tests pass against PostgreSQL | **PASS** | `./mvnw -B test`: `Tests run: 24, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS` (section 5.2). First review: 24 run, 2 failures |
| Links survive an app crash and a recreated database container | **PASS** | Crash re-verified: after `taskkill /PID 14648 /F` and a restart, `GET /qTAQwV` returns 302 with the original `Location` (section 6.3). The container recreate was not repeated, since the r2 brief does not ask for it. `docker-compose.yaml` and the migration are not in the diff, so the first review's result (section 7.5 there) still applies |
| A re-POST after the restart returns 200 with the same code | **PASS** | `https://example.com/r2/20261122231551/one`: `201 qTAQwV` before the kill, `200 qTAQwV` after (sections 6.2, 6.3) |
| A new create runs no select-by-code before its insert | **PASS** | A new create logs 2 statements: `select ... where l1_0.long_url=?`, then `insert into links (long_url,code) values (?,?)`. There is no `where l1_0.code=?` (section 6.2, requests a and "Extra"; also the collision tests in section 5.3) |
| A code collision fails instead of overwriting | **PASS** | Repository: the second `saveAndFlush` runs `insert`, PostgreSQL returns `SQLState: 23505`, and `DataIntegrityViolationException` is thrown. HTTP: `perform()` throws `ServletException` caused by `DataIntegrityViolationException`, and the row still holds URL A. No 201 is sent (section 5.3) |
| A 300-character URL works | **PASS** | `url300CharactersLong_isStoredAndRedirectedUnchanged` passes (`LinkPersistenceTest`, 4/4). Manual: `201 OsKtZm`, and psql shows `len = 300` (sections 6.2, 6.5) |
| The contract is unchanged from Stage 1 | **PASS** | `UrlShortenerAcceptanceTest` (5) and `UrlShortenerErrorCasesTest` (9) pass. Manual: 201 new, 200 same code, 302 with exact `Location`, 404 `{"message":"Unknown code"}` (section 6.2). The diff touches no controller, DTO, validator, generator or exception handler. The 23-case rerun from the first review was not repeated. The only behaviour change is the collision path, a bug fix: 201 plus overwrite becomes a failed request |
| Nothing under `src/main` was modified in this session | **PASS** | `git diff HEAD -- src/main` is empty. `git status` lists only `src/test/.../CodeCollisionHttpTest.java` (section 3.4) |

## 3. Git log, tags and the `src/main` diff

### 3.1 Log and tags

```
$ git log --oneline -15
246399d Open Session in view false
169f6f8 Remove findbyCode() method
6766bba Remove unused imports
0b886ff Add Testcontainers support and implement collision handling for URL shortener
2cebc34 Refactor UrlServiceIMPL to retrieve original URL using code from LinkRepository
81be51e Update CLAUDE.md: Expand project guidelines and hard rules for code contributions
22bb908 Add Spring Data JPA and Lombok dependencies; update Link entity and repository for long URL management
eb34255 Add Link entity and repository for URL management
f941874 Configure Flyway
a516f0c DB config and docker compose for postgres
9015b58 Refactor UrlServiceIMPL: Simplify URL construction and remove unused imports
665908a Add integration test for short URL generation without trailing slash in base URL
1607d0a Add black-box tests for error cases in URL shortener API
6fea8bb Add InvalidCodeException class for handling invalid short code scenarios
cd61f9d Implement URL validation, error handling, and refactor URL creation logic

$ git tag -l
(no output: no tags)
```

Note: `246399d` (the `open-in-view` change) was committed while this review was running. When the session started, the same change was staged but not committed. `git diff HEAD -- src/main` is empty, so the tests and the jar ran the same `src/main` code as `246399d`.

### 3.2 Commits since the first review

| Commit | Files under `src/main` | Notes |
|---|---|---|
| `0b886ff` "Add Testcontainers support and implement collision handling for URL shortener" | none | Contains `pom.xml`, the test files from the first review and the two review documents. No collision handling is in this commit |
| `6766bba` "Remove unused imports" | `Link.java` (+31/-9), `UrlServiceIMPL.java` (-2) | Also contains the `Persistable` change to `Link` (the C1 fix) |
| `169f6f8` "Remove findbyCode() method" | `LinkRepository.java`, `UrlServiceIMPL.java` | D1 |
| `246399d` "Open Session in view false" | `application.yaml` | P2 |

### 3.3 `git diff 2cebc34 -- src/main` (full output)

```diff
$ git diff 2cebc34 -- src/main
diff --git a/src/main/java/com/malinda/url_shortener/entity/Link.java b/src/main/java/com/malinda/url_shortener/entity/Link.java
index 7994fc1..310a672 100644
--- a/src/main/java/com/malinda/url_shortener/entity/Link.java
+++ b/src/main/java/com/malinda/url_shortener/entity/Link.java
@@ -1,22 +1,44 @@
 package com.malinda.url_shortener.entity;
 
-import jakarta.persistence.Column;
-import jakarta.persistence.Entity;
-import jakarta.persistence.Id;
-import jakarta.persistence.Table;
-import lombok.AllArgsConstructor;
+import jakarta.persistence.*;
+import lombok.AccessLevel;
 import lombok.Getter;
 import lombok.NoArgsConstructor;
+import org.springframework.data.domain.Persistable;
 
 @Entity
 @Table(name = "links")
-@AllArgsConstructor
-@NoArgsConstructor
 @Getter
-public class Link {
+@NoArgsConstructor(access = AccessLevel.PROTECTED)
+public class Link implements Persistable<String> {
+
     @Id
     private String code;
 
-    @Column(name = "long_url")
+    @Column(name = "long_url", nullable = false)
     private String longUrl;
+
+    @Transient
+    private boolean isNew = true;
+
+    public Link(String code, String longUrl) {
+        this.code = code;
+        this.longUrl = longUrl;
+    }
+
+    @Override
+    public String getId() {
+        return code;
+    }
+
+    @Override
+    public boolean isNew() {
+        return isNew;
+    }
+
+    @PostPersist
+    @PostLoad
+    public void markNotNew() {
+        this.isNew = false;
+    }
 }
\ No newline at end of file
diff --git a/src/main/java/com/malinda/url_shortener/repo/LinkRepository.java b/src/main/java/com/malinda/url_shortener/repo/LinkRepository.java
index fda051e..6c3d7e3 100644
--- a/src/main/java/com/malinda/url_shortener/repo/LinkRepository.java
+++ b/src/main/java/com/malinda/url_shortener/repo/LinkRepository.java
@@ -9,5 +9,4 @@ import java.util.Optional;
 @Repository
 public interface LinkRepository extends JpaRepository<Link, String> {
     Optional<Link> findByLongUrl(String longUrl);
-    Optional<Link> findByCode(String code);
 }
diff --git a/src/main/java/com/malinda/url_shortener/service/IMPL/UrlServiceIMPL.java b/src/main/java/com/malinda/url_shortener/service/IMPL/UrlServiceIMPL.java
index 31a076f..e963f1f 100644
--- a/src/main/java/com/malinda/url_shortener/service/IMPL/UrlServiceIMPL.java
+++ b/src/main/java/com/malinda/url_shortener/service/IMPL/UrlServiceIMPL.java
@@ -11,9 +11,7 @@ import com.malinda.url_shortener.util.UrlValidator;
 import org.springframework.beans.factory.annotation.Value;
 import org.springframework.stereotype.Service;
 
-import java.util.Map;
 import java.util.Optional;
-import java.util.concurrent.ConcurrentHashMap;
 
 @Service
 public class UrlServiceIMPL implements UrlService {
@@ -58,7 +56,7 @@ public class UrlServiceIMPL implements UrlService {
 
     @Override
     public String redirectToOriginalUrl(String code) {
-        Optional<Link> link = linkRepository.findByCode(code);
+        Optional<Link> link = linkRepository.findById(code);
         if (link.isEmpty()) {
             throw new InvalidCodeException("Unknown code");
         } else {
diff --git a/src/main/resources/application.yaml b/src/main/resources/application.yaml
index 840d5d1..9564be9 100644
--- a/src/main/resources/application.yaml
+++ b/src/main/resources/application.yaml
@@ -10,6 +10,7 @@ spring:
   jpa:
     hibernate:
       ddl-auto: validate
+    open-in-view: false
 
 server:
   port: 8081
```

### 3.4 `git status --short` after the session

```
 M src/test/java/com/malinda/url_shortener/CodeCollisionHttpTest.java
```

## 4. Full contents of every changed `src/main` file

### 4.1 `src/main/java/com/malinda/url_shortener/entity/Link.java`

```java
package com.malinda.url_shortener.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Link implements Persistable<String> {

    @Id
    private String code;

    @Column(name = "long_url", nullable = false)
    private String longUrl;

    @Transient
    private boolean isNew = true;

    public Link(String code, String longUrl) {
        this.code = code;
        this.longUrl = longUrl;
    }

    @Override
    public String getId() {
        return code;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    public void markNotNew() {
        this.isNew = false;
    }
}
```

### 4.2 `src/main/java/com/malinda/url_shortener/repo/LinkRepository.java`

```java
package com.malinda.url_shortener.repo;

import com.malinda.url_shortener.entity.Link;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LinkRepository extends JpaRepository<Link, String> {
    Optional<Link> findByLongUrl(String longUrl);
}
```

### 4.3 `src/main/java/com/malinda/url_shortener/service/IMPL/UrlServiceIMPL.java`

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

import java.util.Optional;

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
        Optional<Link> link = linkRepository.findById(code);
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

### 4.4 `src/main/resources/application.yaml`

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
    open-in-view: false

server:
  port: 8081

app:
  base-url: http://localhost:8081/
```

## 5. Test changes, commands and results

### 5.1 Change to `CodeCollisionHttpTest` (the only test change)

Why: after the C1 fix, the collision reaches the database. The resulting `DataIntegrityViolationException` is not handled in `src/main`, so it escapes `MockMvc.perform()` as a `ServletException`. Before this change, the test would have errored at `perform()` and never reached its checks.

What changed:
- `perform()` is wrapped in a `catch (Exception e)`.
- An escaped exception, or a response of any status, is printed.
- The two existing assertions are untouched and run in every case:
  - the row under code X still holds URL A;
  - if the status is 201, the code returned is not X.

Together they give the required outcome: row X still holds A, and the client never receives 201 with code X. A failed request counts as acceptable, whether `perform()` throws or a 5xx comes back. A future retry fix that returns 201 with a *different* code also passes. No assertion was removed or weakened.

```diff
diff --git a/src/test/java/com/malinda/url_shortener/CodeCollisionHttpTest.java b/src/test/java/com/malinda/url_shortener/CodeCollisionHttpTest.java
index fca3a0f..0424146 100644
--- a/src/test/java/com/malinda/url_shortener/CodeCollisionHttpTest.java
+++ b/src/test/java/com/malinda/url_shortener/CodeCollisionHttpTest.java
@@ -26,6 +26,11 @@ import static org.springframework.test.web.servlet.request.MockMvcRequestBuilder
  * static mock; MockMvc runs the request on this thread) to return X for a POST of a new
  * URL B. The existing link must not be overwritten, and code X must not be handed out
  * for B. The response status is printed so the report can record it.
+ *
+ * Once the collision reaches the database, an unhandled DataIntegrityViolationException
+ * may escape perform() instead of producing a response. Either that exception or a 5xx
+ * response counts as "the request failed"; the stored-row and handed-out-code checks
+ * apply in every case.
  */
 @SpringBootTest
 @AutoConfigureMockMvc
@@ -48,18 +53,28 @@ class CodeCollisionHttpTest {
                 .param("url", urlA)
                 .update();
 
-        MvcResult result;
+        MvcResult result = null;
+        Exception escaped = null;
         try (MockedStatic<ShortCodeGenerator> generator = mockStatic(ShortCodeGenerator.class)) {
             generator.when(ShortCodeGenerator::generateShortCode).thenReturn(takenCode);
             result = mockMvc.perform(post("/api/urls")
                             .contentType(MediaType.APPLICATION_JSON)
                             .content("{\"url\": \"" + urlB + "\"}"))
                     .andReturn();
+        } catch (Exception e) {
+            escaped = e;
         }
 
-        int status = result.getResponse().getStatus();
-        String body = result.getResponse().getContentAsString();
-        System.out.println("[CodeCollisionHttpTest] POST with taken code -> status " + status + ", body " + body);
+        int status = -1;
+        String body = null;
+        if (result != null) {
+            status = result.getResponse().getStatus();
+            body = result.getResponse().getContentAsString();
+            System.out.println("[CodeCollisionHttpTest] POST with taken code -> status " + status + ", body " + body);
+        } else {
+            System.out.println("[CodeCollisionHttpTest] POST with taken code -> perform() threw " + escaped
+                    + (escaped.getCause() != null ? "; cause: " + escaped.getCause() : ""));
+        }
 
         String stored = jdbc.sql("SELECT long_url FROM links WHERE code = :code")
                 .param("code", takenCode)
```

### 5.2 Full suite

Command:

```
./mvnw -B test
```

| Test class | Tests | Result |
|---|---|---|
| `CodeCollisionHttpTest` | 1 | PASS |
| `CodeCollisionTest` | 1 | PASS |
| `LinkPersistenceTest` | 4 | PASS |
| `ShortUrlBaseUrlTest` | 1 | PASS |
| `TestDatabaseIsolationTest` | 2 | PASS |
| `UrlShortenerAcceptanceTest` | 5 | PASS |
| `UrlShortenerApplicationTests` | 1 | PASS |
| `UrlShortenerErrorCasesTest` | 9 | PASS |
| **Total** | **24** | **24 pass, 0 fail, 0 error, 0 skipped** |

There are no failures, so there is no failure output to report. Surefire summary lines, verbatim:

```
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 43.25 s -- in com.malinda.url_shortener.CodeCollisionHttpTest
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 24.31 s -- in com.malinda.url_shortener.CodeCollisionTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.563 s -- in com.malinda.url_shortener.LinkPersistenceTest
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 10.60 s -- in com.malinda.url_shortener.ShortUrlBaseUrlTest
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.120 s -- in com.malinda.url_shortener.TestDatabaseIsolationTest
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.294 s -- in com.malinda.url_shortener.UrlShortenerAcceptanceTest
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.046 s -- in com.malinda.url_shortener.UrlShortenerApplicationTests
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.405 s -- in com.malinda.url_shortener.UrlShortenerErrorCasesTest
Tests run: 24, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time:  02:04 min
```

Line printed by `CodeCollisionHttpTest` in this run:

```
[CodeCollisionHttpTest] POST with taken code -> perform() threw jakarta.servlet.ServletException: Request processing failed: org.springframework.dao.DataIntegrityViolationException: could not execute statement [ERROR: duplicate key value violates unique constraint "links_pkey"
  Detail: Key (code)=(h7afc583) already exists.] [insert into links (long_url,code) values (?,?)]; SQL [insert into links (long_url,code) values (?,?)]; constraint [links_pkey]; cause: org.springframework.dao.DataIntegrityViolationException: could not execute statement [ERROR: duplicate key value violates unique constraint "links_pkey"
  Detail: Key (code)=(h7afc583) already exists.] [insert into links (long_url,code) values (?,?)]; SQL [insert into links (long_url,code) values (?,?)]; constraint [links_pkey]
```

### 5.3 Collision tests with SQL logging

Command:

```
./mvnw -B test -Dtest='CodeCollisionTest,CodeCollisionHttpTest' -Dlogging.level.org.hibernate.SQL=debug
```

Result: 2 run, 0 failures.

```
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 25.84 s -- in com.malinda.url_shortener.CodeCollisionHttpTest
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 7.878 s -- in com.malinda.url_shortener.CodeCollisionTest
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time:  47.088 s
```

**`CodeCollisionTest`** (repository level, two `saveAndFlush` calls with the same code). These are the SQL lines and the database error, verbatim:

```
2026-11-22T23:15:17.405+05:30 DEBUG 18272 --- [url-shortener] [           main] org.hibernate.SQL                        : insert into links (long_url,code) values (?,?)
2026-11-22T23:15:17.425+05:30 DEBUG 18272 --- [url-shortener] [           main] org.hibernate.SQL                        : insert into links (long_url,code) values (?,?)
2026-11-22T23:15:17.429+05:30  WARN 18272 --- [url-shortener] [           main] org.hibernate.orm.jdbc.error             : HHH000247: ErrorCode: 0, SQLState: 23505
2026-11-22T23:15:17.430+05:30  WARN 18272 --- [url-shortener] [           main] org.hibernate.orm.jdbc.error             : ERROR: duplicate key value violates unique constraint "links_pkey"
  Detail: Key (code)=(c6161ec0) already exists.
```

- Statement count: **2**, both `insert`. In the first review there were 4: `select` and `insert`, then `select` and **`update`**.
- The second insert is rejected with `23505` on `links_pkey`, and the test's `assertThatThrownBy(... DataIntegrityViolationException)` passes. The stored URL is still A.

**`CodeCollisionHttpTest`** (HTTP level, generator stubbed to return the taken code). These are the SQL lines, the database error and the outcome, verbatim:

```
2026-11-22T23:15:09.072+05:30 DEBUG 18272 --- [url-shortener] [           main] org.hibernate.SQL                        : select l1_0.code,l1_0.long_url from links l1_0 where l1_0.long_url=?
2026-11-22T23:15:09.292+05:30 DEBUG 18272 --- [url-shortener] [           main] org.hibernate.SQL                        : insert into links (long_url,code) values (?,?)
2026-11-22T23:15:09.314+05:30  WARN 18272 --- [url-shortener] [           main] org.hibernate.orm.jdbc.error             : HHH000247: ErrorCode: 0, SQLState: 23505
2026-11-22T23:15:09.314+05:30  WARN 18272 --- [url-shortener] [           main] org.hibernate.orm.jdbc.error             : ERROR: duplicate key value violates unique constraint "links_pkey"
  Detail: Key (code)=(h5fcddde) already exists.
[CodeCollisionHttpTest] POST with taken code -> perform() threw jakarta.servlet.ServletException: Request processing failed: org.springframework.dao.DataIntegrityViolationException: could not execute statement [ERROR: duplicate key value violates unique constraint "links_pkey"
  Detail: Key (code)=(h5fcddde) already exists.] [insert into links (long_url,code) values (?,?)]; SQL [insert into links (long_url,code) values (?,?)]; constraint [links_pkey]; cause: org.springframework.dao.DataIntegrityViolationException: could not execute statement [ERROR: duplicate key value violates unique constraint "links_pkey"
  Detail: Key (code)=(h5fcddde) already exists.] [insert into links (long_url,code) values (?,?)]; SQL [insert into links (long_url,code) values (?,?)]; constraint [links_pkey]
```

- Statement count: **2** (`select by long_url`, `insert`). No select by code, and no `update`.
- **What the request produced:** no response. `perform()` threw `jakarta.servlet.ServletException: Request processing failed`, caused by `org.springframework.dao.DataIntegrityViolationException` (constraint `links_pkey`).
- The row for the taken code still holds URL A, and no 201 was sent. In the first review, the same request returned **201 `{"code":"<taken code>"}`** and the row's URL changed to B.

## 6. Manual check transcripts

Setup:

```
docker compose up -d --wait
./mvnw -B -DskipTests package
java -jar target/url-shortener-0.0.1-SNAPSHOT.jar --server.port=8090 --app.base-url=http://localhost:8090 --logging.level.org.hibernate.SQL=debug
```

- `--server.port=8090` is needed because `application.yaml` sets port 8081. Another process was already listening on 8081, so I didn't use it.
- The suite had already passed in section 5.2, so the jar was built with `-DskipTests`.
- Database: the existing Compose container `35c6806b3f26` (`postgres:18-alpine`, healthy).
- Run ID: `20261122231551`. Every URL contains it, so every URL is new.
- Each transcript block shows:
  - the curl command and the response;
  - the time taken;
  - every `org.hibernate.SQL` line the app logged during the request, with their count;
  - any WARN or ERROR lines.

### 6.1 Startup WARN lines

**There are no WARN lines** in either startup: `grep -ci warn` returns 0 for the first start (PID 14648) and for the restart (PID 18920). The first review's startup logged `spring.jpa.open-in-view is enabled by default`, and that line is gone. There is no ERROR line either.

Full startup log of the first start, up to `Started UrlShortenerApplication`. The OS user name in the `Starting ...` line is replaced with `<user>`.

```

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

 :: Spring Boot ::                (v4.1.2)

2026-11-22T23:15:59.382+05:30  INFO 14648 --- [url-shortener] [           main] c.m.u.UrlShortenerApplication            : Starting UrlShortenerApplication v0.0.1-SNAPSHOT using Java 21.0.7 with PID 14648 (E:\Projects\URL Shortener\url-shortener\target\url-shortener-0.0.1-SNAPSHOT.jar started by <user> in E:\Projects\URL Shortener\url-shortener)
2026-11-22T23:15:59.393+05:30  INFO 14648 --- [url-shortener] [           main] c.m.u.UrlShortenerApplication            : No active profile set, falling back to 1 default profile: "default"
2026-11-22T23:16:01.982+05:30  INFO 14648 --- [url-shortener] [           main] .s.d.r.c.RepositoryConfigurationDelegate : Bootstrapping Spring Data JPA repositories in DEFAULT mode.
2026-11-22T23:16:02.202+05:30  INFO 14648 --- [url-shortener] [           main] .s.d.r.c.RepositoryConfigurationDelegate : Finished Spring Data repository scanning in 185 ms. Found 1 JPA repository interface.
2026-11-22T23:16:04.105+05:30  INFO 14648 --- [url-shortener] [           main] o.s.boot.tomcat.TomcatWebServer          : Tomcat initialized with port 8090 (http)
2026-11-22T23:16:04.161+05:30  INFO 14648 --- [url-shortener] [           main] o.apache.catalina.core.StandardService   : Starting service [Tomcat]
2026-11-22T23:16:04.162+05:30  INFO 14648 --- [url-shortener] [           main] o.apache.catalina.core.StandardEngine    : Starting Servlet engine: [Apache Tomcat/11.0.27]
2026-11-22T23:16:04.263+05:30  INFO 14648 --- [url-shortener] [           main] b.w.c.s.WebApplicationContextInitializer : Root WebApplicationContext: initialization completed in 4734 ms
2026-11-22T23:16:05.034+05:30  INFO 14648 --- [url-shortener] [           main] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Starting...
2026-11-22T23:16:05.544+05:30  INFO 14648 --- [url-shortener] [           main] com.zaxxer.hikari.pool.HikariPool        : HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection@706f6d26
2026-11-22T23:16:05.547+05:30  INFO 14648 --- [url-shortener] [           main] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Start completed.
2026-11-22T23:16:05.652+05:30  INFO 14648 --- [url-shortener] [           main] org.flywaydb.core.FlywayExecutor         : Database: jdbc:postgresql://localhost:5432/shortener (PostgreSQL 18.6)
2026-11-22T23:16:05.891+05:30  INFO 14648 --- [url-shortener] [           main] o.f.core.internal.command.DbValidate     : Successfully validated 1 migration (execution time 00:00.093s)
2026-11-22T23:16:05.936+05:30  INFO 14648 --- [url-shortener] [           main] o.f.core.internal.command.DbMigrate      : Current version of schema "public": 1
2026-11-22T23:16:05.945+05:30  INFO 14648 --- [url-shortener] [           main] o.f.core.internal.command.DbMigrate      : Schema "public" is up to date. No migration necessary.
2026-11-22T23:16:06.277+05:30  INFO 14648 --- [url-shortener] [           main] org.hibernate.orm.jpa                    : HHH008540: Processing PersistenceUnitInfo [name: default]
2026-11-22T23:16:06.448+05:30  INFO 14648 --- [url-shortener] [           main] org.hibernate.orm.core                   : HHH000001: Hibernate ORM core version 7.4.13.Final
2026-11-22T23:16:07.646+05:30  INFO 14648 --- [url-shortener] [           main] o.s.o.j.p.SpringPersistenceUnitInfo      : No LoadTimeWeaver setup: ignoring JPA class transformer
2026-11-22T23:16:07.893+05:30  INFO 14648 --- [url-shortener] [           main] org.hibernate.orm.connections.pooling    : HHH10001005: Database info:
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
2026-11-22T23:16:09.503+05:30  INFO 14648 --- [url-shortener] [           main] org.hibernate.orm.core                   : HHH000489: No JTA platform available (set 'hibernate.transaction.jta.platform' to enable JTA platform integration)
2026-11-22T23:16:09.600+05:30  INFO 14648 --- [url-shortener] [           main] j.LocalContainerEntityManagerFactoryBean : Initialized JPA EntityManagerFactory for persistence unit 'default'
2026-11-22T23:16:09.812+05:30  INFO 14648 --- [url-shortener] [           main] o.s.d.j.r.query.QueryEnhancerFactories   : Hibernate is in classpath; If applicable, HQL parser will be used.
2026-11-22T23:16:11.358+05:30  INFO 14648 --- [url-shortener] [           main] o.s.boot.tomcat.TomcatWebServer          : Tomcat started on port 8090 (http) with context path '/'
2026-11-22T23:16:11.400+05:30  INFO 14648 --- [url-shortener] [           main] c.m.u.UrlShortenerApplication            : Started UrlShortenerApplication in 12.885 seconds (process running for 13.498)
```

### 6.2 Step 3.2: new URL, same URL again, GET its code, GET an unknown code

```
### a. POST new URL
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{"url": "https://example.com/r2/20261122231551/one"}'
HTTP/1.1 201 
Content-Type: application/json

{"code":"qTAQwV","shortUrl":"http://localhost:8090/qTAQwV"}

[elapsed: 1.495s]
[SQL statements logged: 2]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.long_url=?
  SQL: insert into links (long_url,code) values (?,?)
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]

### b. POST same URL again
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{"url": "https://example.com/r2/20261122231551/one"}'
HTTP/1.1 200 
Content-Type: application/json

{"code":"qTAQwV","shortUrl":"http://localhost:8090/qTAQwV"}

[elapsed: 0.146s]
[SQL statements logged: 1]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.long_url=?
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]
```

```
### c. GET qTAQwV
$ curl -s -i http://localhost:8090/qTAQwV
HTTP/1.1 302 
Location: https://example.com/r2/20261122231551/one
Content-Length: 0


[elapsed: 0.131s]
[SQL statements logged: 1]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]

### d. GET unknown code
$ curl -s -i http://localhost:8090/zzzzz9
HTTP/1.1 404 
Content-Type: application/json

{"message":"Unknown code"}

[elapsed: 0.154s]
[SQL statements logged: 1]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]
```

An extra request for the 300-character criterion:

```
### Extra: POST 300-character URL
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{"url": "https://example.com/r2/20261122231551/long/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"}'
HTTP/1.1 201 
Content-Type: application/json

{"code":"OsKtZm","shortUrl":"http://localhost:8090/OsKtZm"}

[elapsed: 0.380s]
[SQL statements logged: 2]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.long_url=?
  SQL: insert into links (long_url,code) values (?,?)
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]
```

| Request | Status | Body / Location | SQL statements |
|---|---|---|---|
| a. POST new URL | 201 | `{"code":"qTAQwV","shortUrl":"http://localhost:8090/qTAQwV"}` | 2: select by `long_url`, insert |
| b. POST same URL | 200 | same code `qTAQwV` | 1: select by `long_url` |
| c. GET `/qTAQwV` | 302 | `Location: https://example.com/r2/20261122231551/one` | 1: select by `code` |
| d. GET `/zzzzz9` | 404 | `{"message":"Unknown code"}` | 1: select by `code` |
| Extra: POST 300-char URL | 201 | `OsKtZm` | 2: select by `long_url`, insert |

First review, for comparison: request a logged 3 statements (select by `long_url`, select by `code`, insert). Requests b, c and d were the same.

### 6.3 Step 3.3: hard kill, restart with the same command

```
$ taskkill /PID 14648 /F
SUCCESS: The process with PID 14648 has been terminated.
```

The restart used the same `java -jar ...` command (new PID 18920, started in 15.6 s, 0 WARN lines).

```
### 3a. GET qTAQwV after hard kill and restart
$ curl -s -i http://localhost:8090/qTAQwV
HTTP/1.1 302 
Location: https://example.com/r2/20261122231551/one
Content-Length: 0


[elapsed: 0.727s]
[SQL statements logged: 1]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.code=?
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]

### 3b. re-POST https://example.com/r2/20261122231551/one after restart
$ curl -s -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{"url": "https://example.com/r2/20261122231551/one"}'
HTTP/1.1 200 
Content-Type: application/json

{"code":"qTAQwV","shortUrl":"http://localhost:8090/qTAQwV"}

[elapsed: 0.976s]
[SQL statements logged: 1]
  SQL: select l1_0.code,l1_0.long_url from links l1_0 where l1_0.long_url=?
[WARN/ERROR/exception log lines: none; stack-trace frames: 0]
```

| Request | Status | Code | SQL statements |
|---|---|---|---|
| GET `/qTAQwV` after the kill | 302 to `https://example.com/r2/20261122231551/one` | `qTAQwV` | 1: select by `code` |
| re-POST `.../one` after the kill | 200 | `qTAQwV` (same as before the kill) | 1: select by `long_url` |

### 6.4 Step 3.4: stop the app, leave PostgreSQL running

```
$ taskkill /PID 18920 /F
SUCCESS: The process with PID 18920 has been terminated.
```

- After the stop, port 8090 is free.
- `url-shortener-postgres-1` (`35c6806b3f26`) is still up and healthy.
- I did not touch the process on port 8081.

### 6.5 psql: the rows this run created

```
docker exec url-shortener-postgres-1 psql -U shortener -d shortener -c "select code, length(long_url) len, left(long_url,60) from links where long_url like 'https://example.com/r2/20261122231551/%' order by code;"
```

```
  code  | len |                             left                             
--------+-----+--------------------------------------------------------------
 OsKtZm | 300 | https://example.com/r2/20261122231551/long/xxxxxxxxxxxxxxxxx
 qTAQwV |  41 | https://example.com/r2/20261122231551/one
(2 rows)
```

There are exactly 2 rows, one per new URL: the repeat POST and the re-POST after the restart did not insert anything.

## 7. Status of every first-review finding

Line numbers refer to the current files in section 4.

| ID | Status | Evidence |
|---|---|---|
| **C1** collision silently overwrites, 201 | **Fixed** | `Link.java` line 13 (`implements Persistable<String>`), lines 21-22 (`@Transient boolean isNew = true`), lines 34-37 (`isNew()`) and lines 39-43 (`@PostPersist @PostLoad markNotNew()`). `CodeCollisionTest` and `CodeCollisionHttpTest` pass. SQL: `insert`, then `23505 ... "links_pkey"`, and no `update` (section 5.3) |
| **C2** persistence errors reach the client as a generic 500 with a stack trace | **Open** | `GlobalExceptionHandler` is not in the diff, and it still handles only `InvalidUrlException` and `InvalidCodeException`. The collision path now exists: `DataIntegrityViolationException` escapes the controller (`ServletException` from `perform()`, section 5.3). In the running app, Spring's default handling makes that a 500 with the default body and an ERROR stack trace [code reading; the same path as the observed database-down 500 in the first review, section 7.7]. The database-down case was not re-tested in r2 |
| **I1** duplicate check and insert are not atomic; no unique constraint on `long_url` | **Parked** (unchanged) | `UrlServiceIMPL` line 32 (`findByLongUrl`) and line 47 (`save`) still run in separate repository transactions. There is no `@Transactional` in `src/main`, and `V1__create_links.sql` is unchanged |
| **I2** entity mapping looser than the table | **Open** (partly addressed) | `Link.java` line 18 now declares `nullable = false` for `long_url`. `code` (lines 15-16) still has no `@Column`, so no `length = 10`, no `nullable = false` and no explicit name |
| **P1** extra primary-key SELECT before every INSERT | **Fixed** | A new create logs 2 statements and no `where l1_0.code=?` (section 6.2 request a, section 5.3) |
| **P2** `open-in-view` on by default | **Fixed** | `application.yaml` line 13 `open-in-view: false` (commit `246399d`). Startup WARN count is 0, and the JpaWebConfiguration warning is gone (section 6.1) |
| **D1** `findByCode` duplicates `findById` | **Fixed** | `LinkRepository` now declares only `findByLongUrl` (line 11). `UrlServiceIMPL` line 59 uses `findById(code)`. The SQL is unchanged: `select ... where l1_0.code=?` (section 6.2) |
| **D2** `@Repository` on a Spring Data interface is redundant | **Open** | `LinkRepository.java` line 9 |
| **D3** (from Stage 1) error responses have several shapes | **Open** | The handler is unchanged. Each new collision failure adds a case that uses Spring's default body (see C2) |
| **D4** (from Stage 1) code generation is a static call | **Open** | `UrlServiceIMPL` line 45 `ShortCodeGenerator.generateShortCode()`. `CodeCollisionHttpTest` still needs `mockStatic` |
| **S1** unused `Map`/`ConcurrentHashMap` imports | **Fixed** | Removed in `6766bba` (diff hunk `@@ -11,9 +11,7 @@`). The imports in `UrlServiceIMPL` lines 1-14 are all used |
| **S2** indentation, missing space, no final newline | **Open** | `UrlServiceIMPL` lines 34-43: the `if` block is still indented one level deeper. Line 46 is still `new Link(code,longUrl)`. `Link.java` still has no newline at the end of the file (the diff shows `\ No newline at end of file` on the new side) |
| **S3** public no-argument constructor on the entity | **Fixed** | `Link.java` line 12 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`. `@AllArgsConstructor` was removed and replaced by an explicit 2-argument constructor (lines 24-27) |

The other items carried over from Stage 1 are unchanged, because `UrlController`, `ShortCodeGenerator`, the DTOs and the exceptions are not in the diff:
- D1 (the service builds the DTO)
- D2 (`@Value` field injection, now `UrlServiceIMPL` lines 21-22)
- D4 (`redirectToOriginalUrl` naming)
- D5 (base-URL normalisation)
- S1 to S5

The parked items in section 11 of the first review are unchanged, with one exception: the collision symptom is now a failed request instead of an overwrite (see N1).

## 8. New findings in the diff, by severity

Each finding is tagged **[observed]** or **[code reading]**. Line numbers refer to section 4.

### 8.1 Correctness

**N1. A code collision now fails the user's request outright, with no retry.** [observed in `CodeCollisionHttpTest`; status by code reading]
- *Where:* `UrlServiceIMPL` lines 45-47 (generate a code once, then `save`), together with `GlobalExceptionHandler` (unchanged).
- *What:* When the random code is taken, the request ends with an unhandled `DataIntegrityViolationException`. The client asked to shorten a valid, new URL and gets a failure instead (500 by default, per C2). Nothing generates a new code and tries again.
- *Why it matters:* This is much better than the first review's silent overwrite, because no existing link is lost. But a valid request now fails for a reason the client cannot fix, and the chance of that grows with the table (see the parked "random-code collisions" item). This is C2 made reachable, and it is the decision C1's fix direction left open ("then decide what a collision should do").

### 8.2 Concurrency / data integrity

None new. The `Persistable` change makes the primary key the actual guard against two writers using the same code, and the test shows the database rejecting the second insert. I1 (the URL check and the insert are not atomic) is unchanged and still parked.

### 8.3 Performance

None. A create is now 2 statements, and a GET and a duplicate POST are 1 each (section 6.2).

### 8.4 Design

**N2. The new-ness flag is managed through a public lifecycle method.** [code reading]
- *Where:* `Link.java` lines 39-43 (`public void markNotNew()`).
- *What:* The JPA callback that flips `isNew` is part of the entity's public API, so any caller can mark a fresh `Link` as "not new". If that happened before `save`, it would bring back the `merge` behaviour behind C1. JPA lifecycle callbacks don't need to be public. This is minor.

**N3. `Link` exposes the primary key through two getters.** [code reading]
- *Where:* `Link.java` line 11 (Lombok `@Getter` generates `getCode()`) and lines 29-32 (`getId()` from `Persistable`).
- *What:* Both return `code`. The service uses `getCode()` (`UrlServiceIMPL` line 36). It is harmless, but there are now two names for one value. This is a nit.

### 8.5 Style and process

**N4. Wildcard import in the entity.** [code reading] `Link.java` line 3 uses `import jakarta.persistence.*;`. This is the same kind of issue as Stage 1's S1 (the wildcard import in `UrlController`).

**N5. Commit messages don't describe their content.** [observed: `git show --stat`, section 3.2]
- `0b886ff` says "implement collision handling", but it contains only tests, `pom.xml` and the review documents.
- The actual collision fix (`Link implements Persistable`) is in `6766bba`, "Remove unused imports".
- Anyone reading the history to find the C1 fix will look in the wrong place.

### 8.6 What's done well in the diff

- **The C1 fix uses the supported Spring Data mechanism.** `Persistable` with a transient flag means `save` uses `persist` for new links and `merge` never runs. The flag clears on `@PostLoad` and `@PostPersist`, so loaded rows and saved rows both report "not new".
- **The primary key now does its job.** A taken code is rejected by PostgreSQL (`23505`) instead of being overwritten. The first review asked to "let the primary key prevent it", and it now does.
- **`open-in-view` is set explicitly**, so each repository call holds a connection only for its own transaction. After a failed insert, the persistence context is discarded with that transaction and is not reused later in the request.
- **The repository is smaller:** the redundant finder is gone, and lookups by code use the inherited `findById`.
- **The entity is tighter:** it has a protected no-argument constructor for JPA only, an explicit constructor for application code, and `nullable = false` on `long_url`.
- **Each first-review finding got its own small change**, and the diff touches nothing beyond those findings.

I did not write, edit or delete anything under `src/main` in this session.
