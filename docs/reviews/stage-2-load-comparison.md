# URL Shortener: Stage 1 vs Stage 2 load comparison (k6)

- **Date:** 2026-11-22. Local time is UTC+05:30; k6 timestamps are UTC.
- **Order:** the runs alternated: Stage 1, Stage 2, Stage 1, Stage 2.
- **Script:** `load-test/baseline.js`, unmodified.
  - Setup: a pool of 1000 links.
  - Warm-up: 50 iterations/s for 20 s.
  - Measured phase: 120 s, starting at 25 s, with create at 20/s and redirect at 200/s, run at the same time.
  - Thresholds: `checks rate==1`, `http_req_failed rate==0`.
- **k6 command (all runs):** `docker run --rm -i grafana/k6 run - < load-test/baseline.js`, using image `grafana/k6:latest` (`3e4186aea62e`).
- **Full k6 output:** `load-test/results/compare-run<N>-stage<1|2>.txt`.

## Machine state before the runs

Sampled before the builds and the first run:

| Item | Value |
|---|---|
| Free RAM | 0.89 GB of 7.70 GB |
| Total CPU % (3 samples, 2 s apart) | 31.7, 9.7, 7.9 |
| Logical CPUs | 8 |
| Listening on 8081 / 8090 | nothing |
| Project java processes | none |
| `git tag -l` | `stage-1`, `stage-2` |

Largest processes by working set:

| Process | Count | Working set (MB) |
|---|---|---|
| Memory Compression | 1 | 1378 |
| chrome | 18 | 636 |
| msedge | 11 | 633 |
| vmmemWSL | 1 | 403 |
| svchost | 90 | 357 |
| claude | 2 | 284 |
| MsMpEng | 1 | 187 |
| explorer | 1 | 163 |
| Docker Desktop | 4 | 115 |

Heavy processes present: the browsers Chrome (18 processes) and Edge (11 processes), plus Docker Desktop and WSL. No IDE or media-player process was in the list.

Free RAM just before each k6 start:

| Run | Version | Free RAM | Sampled |
|---|---|---|---|
| 1 | Stage 1 | 0.68 GB | 23:39:17 |
| 2 | Stage 2 | 0.53 GB | 23:42:44 |
| 3 | Stage 1 | 0.68 GB | 23:45:55 |
| 4 | Stage 2 | 0.66 GB | 23:48:50 |

## Jars

| Version | Built in | Commit | Command |
|---|---|---|---|
| Stage 1 | worktree `../url-shortener-stage1` (`git worktree add ../url-shortener-stage1 stage-1`, detached HEAD) | `9015b589fabb92b387564285f33358f0cf8f8af0` (`9015b58` "Refactor UrlServiceIMPL: Simplify URL construction and remove unused imports"), tag `stage-1` | `./mvnw -B -q -DskipTests package` |
| Stage 2 | current checkout, branch `dev` | `dc7f88caa0b3c7fccb6c0e9917673eacbe0ee955` (`dc7f88c` "Stage 2 Claude test and review"), tag `stage-2` = HEAD. `src/main` and `pom.xml` had no uncommitted changes | `./mvnw -B -q -DskipTests package` |

Start commands:
- Stage 1: `java -jar ../url-shortener-stage1/target/url-shortener-0.0.1-SNAPSHOT.jar --server.port=8090 --app.base-url=http://localhost:8090`
- Stage 2: `java -jar target/url-shortener-0.0.1-SNAPSHOT.jar --server.port=8090 --app.base-url=http://localhost:8090 --spring.datasource.url=jdbc:postgresql://localhost:5432/shortener_load`

Before each Stage 2 run, `shortener_load` was dropped and recreated. Flyway's startup log shows V1 applied to it both times:
- Run 2: `Database: jdbc:postgresql://localhost:5432/shortener_load (PostgreSQL 18.6)`, `Migrating schema "public" to version "1 - create links"`, `Successfully applied 1 migration to schema "public", now at version v1 (execution time 00:00.082s)`
- Run 4: the same lines, with `execution time 00:00.039s`

Readiness check: an invalid POST (`{"url":"not-a-url"}`) returned 400.

| Run | App PID | Started in | Ready after | Startup WARN/ERROR lines | App WARN/ERROR lines during the run | Port 8090 free after `taskkill /F` |
|---|---|---|---|---|---|---|
| 1 | 24752 | 6.689 s | ~3 s | 0 | 0 | yes |
| 2 | 11300 | 12.592 s | ~5 s | 0 | 0 | yes |
| 3 | 14624 | 6.631 s | ~3 s | 0 | 0 | yes |
| 4 | 24356 | 16.431 s | ~6 s | 0 | 0 | yes |

## create_latency

| Run | Version | count | avg | med | p(95) | p(99) | max |
|---|---|---|---|---|---|---|---|
| 1 | Stage 1 | 2401 | 2.06ms | 1.81ms | 3.37ms | 6.49ms | 57.69ms |
| 2 | Stage 2 | 2401 | 16.22ms | 11.64ms | 45.18ms | 94.75ms | 189.59ms |
| 3 | Stage 1 | 2400 | 2.25ms | 1.81ms | 4.1ms | 11.81ms | 47.78ms |
| 4 | Stage 2 | 2401 | 11.56ms | 9.11ms | 26.32ms | 51.73ms | 94.72ms |

## redirect_latency

| Run | Version | count | avg | med | p(95) | p(99) | max |
|---|---|---|---|---|---|---|---|
| 1 | Stage 1 | 24001 | 1.6ms | 1.43ms | 2.62ms | 4.42ms | 65.01ms |
| 2 | Stage 2 | 23995 | 9.26ms | 6.3ms | 27.06ms | 62.04ms | 135.5ms |
| 3 | Stage 1 | 24001 | 1.74ms | 1.4ms | 2.94ms | 9.08ms | 62ms |
| 4 | Stage 2 | 24000 | 6.43ms | 4.89ms | 14.94ms | 32.58ms | 89.54ms |

## Rates, checks and thresholds per run

The achieved create and redirect rates are the `count` of each latency trend divided by the 120 s measured phase. k6 prints no per-scenario rate in its summary. `dropped_iterations` is absent from the k6 summary when it is 0.

| Run | Version | Create rate (/s) | Redirect rate (/s) | dropped_iterations | checks rate | http_req_failed rate | http_reqs | Thresholds |
|---|---|---|---|---|---|---|---|---|
| 1 | Stage 1 | 20.01 | 200.01 | 0 | 99.99% (51402 / 51404; 2 failed) | 0.00% (1 / 29404) | 29404 (172.56/s) | **failed** (`checks` ✗, `http_req_failed` ✗); k6 exit code 99 |
| 2 | Stage 2 | 20.01 | 199.96 | 6 (0.037886/s) | 99.99% (51389 / 51391; 2 failed) | 0.00% (1 / 29396) | 29396 (185.62/s) | **failed** (`checks` ✗, `http_req_failed` ✗); k6 exit code 99 |
| 3 | Stage 1 | 20.00 | 200.01 | 8 (0.053322/s) | 99.99% (51392 / 51395; 3 failed) | 0.00% (2 / 29387) | 29387 (195.87/s) | **failed** (`checks` ✗, `http_req_failed` ✗); k6 exit code 99 |
| 4 | Stage 2 | 20.01 | 200.00 | 0 | 99.98% (51396 / 51402; 6 failed) | 0.01% (3 / 29403) | 29403 (183.71/s) | **failed** (`checks` ✗, `http_req_failed` ✗); k6 exit code 99 |

Failed checks per run:

| Run | `create: status is 201` | `redirect: status is 302` | `redirect: Location header present` | `warmup: create returns 201` |
|---|---|---|---|---|
| 1 | 0 failed | 1 failed (24000 passed) | 1 failed (24000 passed) | 0 failed |
| 2 | 0 failed | 1 failed (23994 passed) | 1 failed (23994 passed) | 0 failed |
| 3 | 1 failed (2399 passed) | 1 failed (24000 passed) | 1 failed (24000 passed) | 0 failed |
| 4 | 0 failed | 3 failed (23997 passed) | 3 failed (23997 passed) | 0 failed |

## Stage 2 runs: database at the end of the run

These were sampled after k6 finished and before the app was stopped.

| Run | `SELECT count(*) FROM links;` | `pg_size_pretty(pg_total_relation_size('links'))` | Postgres CPU % | Postgres memory usage / limit | MEM % | NET I/O | BLOCK I/O | PIDS |
|---|---|---|---|---|---|---|---|---|
| 2 | 4401 | 664 kB | 0.39% | 64.75MiB / 3.678GiB | 1.72% | 11.8MB / 10.6MB | 24.6kB / 9.86MB | 19 |
| 4 | 4402 | 648 kB | 0.00% | 64.32MiB / 3.678GiB | 1.71% | 23MB / 20.5MB | 24.6kB / 14.8MB | 19 |

`docker stats --no-stream` (container `35c6806b3f26`, `url-shortener-postgres-1`), verbatim:

```
# run 2
CONTAINER ID   NAME                       CPU %     MEM USAGE / LIMIT     MEM %     NET I/O           BLOCK I/O         PIDS
35c6806b3f26   url-shortener-postgres-1   0.39%     64.75MiB / 3.678GiB   1.72%     11.8MB / 10.6MB   24.6kB / 9.86MB   19
# run 4
CONTAINER ID   NAME                       CPU %     MEM USAGE / LIMIT     MEM %     NET I/O         BLOCK I/O         PIDS
35c6806b3f26   url-shortener-postgres-1   0.00%     64.32MiB / 3.678GiB   1.71%     23MB / 20.5MB   24.6kB / 14.8MB   19
```

## k6 warnings and errors (verbatim)

These are all the `level=` lines in the four output files. There are no other warning or error lines.

Run 1 (Stage 1):
```
time="2026-11-22T18:12:09Z" level=warning msg="Request Failed" error="Get \"http://host.docker.internal:8090/APRrvD\": dial: i/o timeout"
time="2026-11-22T18:12:10Z" level=error msg="thresholds on metrics 'checks, http_req_failed' have been crossed"
```

Run 2 (Stage 2):
```
time="2026-11-22T18:13:54Z" level=warning msg="Request Failed" error="Get \"http://host.docker.internal:8090/YSVVRy\": dial: i/o timeout"
time="2026-11-22T18:15:25Z" level=error msg="thresholds on metrics 'checks, http_req_failed' have been crossed"
```

Run 3 (Stage 1):
```
time="2026-11-22T18:17:46Z" level=warning msg="Request Failed" error="Post \"http://host.docker.internal:8090/api/urls\": dial: i/o timeout"
time="2026-11-22T18:18:26Z" level=warning msg="Request Failed" error="Get \"http://host.docker.internal:8090/pBNdtg\": dial: i/o timeout"
time="2026-11-22T18:18:26Z" level=error msg="thresholds on metrics 'checks, http_req_failed' have been crossed"
```

Run 4 (Stage 2):
```
time="2026-11-22T18:21:02Z" level=warning msg="Request Failed" error="Get \"http://host.docker.internal:8090/7b36wW\": dial: i/o timeout"
time="2026-11-22T18:21:02Z" level=warning msg="Request Failed" error="Get \"http://host.docker.internal:8090/jEUzSC\": dial: i/o timeout"
time="2026-11-22T18:21:02Z" level=warning msg="Request Failed" error="Get \"http://host.docker.internal:8090/twhODZ\": dial: i/o timeout"
time="2026-11-22T18:21:33Z" level=error msg="thresholds on metrics 'checks, http_req_failed' have been crossed"
```

The app logged 0 WARN or ERROR lines during each of the four runs.

## After the runs

- `shortener_load` was dropped.
- `git worktree remove ../url-shortener-stage1` succeeded, and `git worktree list` now shows only the main checkout.
- Postgres is still running (`url-shortener-postgres-1`, healthy). Ports 8081 and 8090 are free.
- Nothing under `src/main`, `src/test`, `pom.xml`, the application config, `docker-compose.yaml` or `load-test/baseline.js` was modified.
- Nothing was committed, tagged or pushed.
- New untracked files: the four `load-test/results/compare-run*.txt` files and this report.
