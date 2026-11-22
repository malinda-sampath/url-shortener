# CLAUDE.md

Guidance for Claude Code in this repository.

## What this project is

A URL shortener in Spring Boot, built as a **learning project**. The point is to learn system design by hitting real problems one stage at a time, not just to ship the app. The design work and mentoring happen in a separate chat. In this repo, Claude Code is a **test runner and test/infra helper**, not a co-author of the application.

## Hard rules

- **Do not write or modify application code** (anything under `src/main/`): no controllers, services, entities, repositories, configs or "quick fixes". This holds even when a test fails and the fix looks obvious.
- When a test fails, **report it, don't fix it**: name the failing test, the assertion or exception, and the file or line it points to. Stop there. Don't propose a code patch unless I ask for one.
- **Allowed when I ask:** complete tests (`src/test/`), load-test scripts (k6 or JMeter, under `load-tests/`), and infrastructure config (Docker Compose, Nginx and similar).
- Don't add dependencies, tools or infrastructure (cache, queue, load balancer and so on) on your own. Each one is brought in by a specific stage, only when that stage's problem justifies it.
- Don't refactor, reformat or "tidy" code you weren't asked to touch.

## Current state

- **Stage 1 (done):** in-memory map storing code → URL.
- **Stage 2 (in progress):** persistence with **PostgreSQL** via **Spring Data JPA**, so links survive restarts. One `links` table, with the short code as the primary key.
- Agreed API behaviour:
  - `POST` to create a short link returns **201** for a new URL.
  - Submitting a URL that already exists returns the **existing** short link with **200** (not a new code, not a 409).
  - `GET /{code}` redirects to the original URL.
- The progress log lives in the claude.ai project ("URL Shortener: Project Context"), not in this repo.

## Tech stack

- Java [version], Spring Boot [version], [Maven/Gradle]
- PostgreSQL (local, via Docker Compose)
- k6 (or JMeter) for load tests

## Commands

Fill in once the build tool is fixed:

```bash
# Start local infrastructure
docker compose up -d

# Run all tests
./mvnw test            # or: ./gradlew test

# Run one test class
./mvnw test -Dtest=ClassName        # or: ./gradlew test --tests ClassName

# Run the app
./mvnw spring-boot:run              # or: ./gradlew bootRun

# Load test
k6 run load-tests/<script>.js
```

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
