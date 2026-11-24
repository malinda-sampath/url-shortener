# CLAUDE.md

Guidance for Claude Code in this repository.

## What this project is

A URL shortener in Spring Boot, built as a **learning project**. The point is to learn system design by hitting real problems one stage at a time, not just to ship the app. The design work and mentoring happen in a separate chat. In this repo, Claude Code is a **test runner and test/infra helper**, not a co-author of the application.

## Hard rules

- **Do not write or modify application code** (anything under `src/main/`): no controllers, services, entities, repositories, configs or "quick fixes". This holds even when a test fails and the fix looks obvious.
- When a test fails, **report it, don't fix it**: name the failing test, the assertion or exception, and the file or line it points to. Stop there. Don't propose a code patch unless I ask for one.
- **Allowed when I ask:** complete tests (`src/test/`), load-test scripts (k6, under `load-test/`), and infrastructure config (Docker Compose, Nginx and similar).
- Don't add dependencies, tools or infrastructure (cache, queue, load balancer and so on) on your own. Each one is brought in by a specific stage, only when that stage's problem justifies it.
- Don't refactor, reformat or "tidy" code you weren't asked to touch.

## Current state

- **Stage 1 (done):** in-memory map storing code → URL.
- **Stage 2 (done):** persistence with **PostgreSQL** via **Spring Data JPA**, so links survive restarts. One `links` table, with the short code as the primary key.
- **Stage 3:** not started yet. Its problem gets set in the mentoring chat, so don't anticipate it here.
- Agreed API behaviour:
  - `POST` to create a short link returns **201** for a new URL.
  - Submitting a URL that already exists returns the **existing** short link with **200** (not a new code, not a 409).
  - `GET /{code}` redirects to the original URL.
- Each finished stage is tagged in git (`stage-1`, `stage-2`). Reviews and load comparisons are in `docs/reviews/`, and k6 results are in `load-test/results/`.
- Open review findings carried past Stage 2 are listed in `docs/reviews/stage-2-review-r2.md` (section 7). Treat them as facts to report on, not work to do.
- The progress log lives in the claude.ai project ("URL Shortener: Project Context"), not in this repo.

## Tech stack

- Java 21, Spring Boot 4.1.2, Maven (`./mvnw`)
- PostgreSQL 18 (`postgres:18-alpine`, local via `docker-compose.yaml`), schema owned by Flyway (`src/main/resources/db/migration`), `ddl-auto: validate`
- Tests: JUnit 5, MockMvc, Testcontainers PostgreSQL (`TestcontainersConfiguration`, `@ServiceConnection`)
- k6 (run via Docker) for load tests

## Commands

```bash
# Start local infrastructure (dev database "shortener" on 127.0.0.1:5432)
docker compose up -d --wait

# Run all tests (Docker must be running: tests start their own PostgreSQL via Testcontainers)
./mvnw -B test

# Run one test class
./mvnw test -Dtest=ClassName

# Run the app (default port 8081, from application.yaml)
./mvnw spring-boot:run

# Run the jar for manual checks or load tests (settings via command-line arguments, never by editing config)
./mvnw -B -DskipTests package
java -jar target/url-shortener-0.0.1-SNAPSHOT.jar --server.port=8090 --app.base-url=http://localhost:8090

# Load test (script on stdin; it targets host.docker.internal:8090)
docker run --rm -i grafana/k6:2.3.0 run - < load-test/baseline.js
# PowerShell: Get-Content load-test/baseline.js | docker run --rm -i grafana/k6:2.3.0 run -
```

- Load tests against Stage 2+ use a throwaway database (`shortener_load`, passed via `--spring.datasource.url`), so dev data in `shortener` stays untouched.
- Build an older stage's jar from its tag in a separate worktree (`git worktree add ../url-shortener-stage1 stage-1`), then remove the worktree afterwards.

## Writing tests (when asked)

- Make tests **reproduce the problem**: a failing test that shows the bug or limit is the goal, not a passing one.
- Prefer integration tests against a real PostgreSQL (Testcontainers or the Compose database) over mocking the repository, since stage problems usually show up at the database layer.
- Concurrency tests should actually run requests in parallel (for example an `ExecutorService` with a `CountDownLatch`) and assert on the outcome, such as duplicate codes or lost writes.
- Name tests after the behaviour being checked, e.g. `duplicateUrl_returnsExistingCodeWith200`.

## Reporting results

After running tests or a load test, report:

- Pass/fail counts, and for each failure the test name with a one-line cause
- For load tests: requests per second, error rate, and latency at **p50 / p95 / p99**
- The exact command used, so the run can be repeated for a before/after comparison

Keep reports short and factual. Diagnosing root causes is my job, so describe the symptom and leave the cause to me unless I ask.
