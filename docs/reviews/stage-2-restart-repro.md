# Stage 2: restart reproduction

Run on 2026-11-12 (local time UTC+05:30). HTTP `Date` headers are in GMT.

- App: `target/url-shortener-0.0.1-SNAPSHOT.jar`, started with `java -jar target/url-shortener-0.0.1-SNAPSHOT.jar --server.port=8090` from Git Bash.
- Before starting: no `java.exe` processes were running and nothing was listening on port 8090.
- Readiness check: `POST /api/urls` with body `{"url":"not-a-url"}`, repeated every second until a non-connection-error status came back. Both times it returned `400`.
- Headers trimmed from the responses below: `Date`, `Transfer-Encoding`, `Content-Length`.

## Summary

| URL | code | status before restart | status after restart | Location after restart |
|---|---|---|---|---|
| https://shop.example.com/campaign/1 | `djm2he` | 302 | 404 | (none) |
| https://shop.example.com/campaign/2 | `ZSmM2O` | 302 | 404 | (none) |
| https://shop.example.com/campaign/3 | `fwDXaK` | 302 | 404 | (none) |

Re-POSTing `https://shop.example.com/campaign/1` after the restart returned `201` with code `CEmDcK`. That does not match the code from step 2 (`djm2he`).

## 1. First start

App ready after about 2 s (Windows PID 21080).

## 2. Create three links

```
$ curl -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{"url":"https://shop.example.com/campaign/1"}'
HTTP/1.1 201
Content-Type: application/json

{"code":"djm2he","shortUrl":"http://localhost:8081/djm2he"}

$ curl -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{"url":"https://shop.example.com/campaign/2"}'
HTTP/1.1 201
Content-Type: application/json

{"code":"ZSmM2O","shortUrl":"http://localhost:8081/ZSmM2O"}

$ curl -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{"url":"https://shop.example.com/campaign/3"}'
HTTP/1.1 201
Content-Type: application/json

{"code":"fwDXaK","shortUrl":"http://localhost:8081/fwDXaK"}
```

## 3. Follow each code (before restart)

curl does not follow redirects without `-L`.

```
$ curl -i http://localhost:8090/djm2he
HTTP/1.1 302
Location: https://shop.example.com/campaign/1

$ curl -i http://localhost:8090/ZSmM2O
HTTP/1.1 302
Location: https://shop.example.com/campaign/2

$ curl -i http://localhost:8090/fwDXaK
HTTP/1.1 302
Location: https://shop.example.com/campaign/3
```

## 4. Stop and restart

```
$ kill -INT <bash pid of java>          # Windows PID 21080
(process still alive after 20 s)
$ taskkill /PID 21080 /F
SUCCESS: The process with PID 21080 has been terminated.
$ tasklist /FI "IMAGENAME eq java.exe"
INFO: No tasks are running which match the specified criteria.
```

After that, nothing was listening on port 8090. The second start was ready after about 2 s (Windows PID 17620).

## 5. Follow each code (after restart)

```
$ curl -i http://localhost:8090/djm2he
HTTP/1.1 404
Content-Type: application/json

{"message":"Unknown code"}

$ curl -i http://localhost:8090/ZSmM2O
HTTP/1.1 404
Content-Type: application/json

{"message":"Unknown code"}

$ curl -i http://localhost:8090/fwDXaK
HTTP/1.1 404
Content-Type: application/json

{"message":"Unknown code"}
```

## 6. Re-create campaign/1

```
$ curl -i -X POST http://localhost:8090/api/urls -H 'Content-Type: application/json' -d '{"url":"https://shop.example.com/campaign/1"}'
HTTP/1.1 201
Content-Type: application/json

{"code":"CEmDcK","shortUrl":"http://localhost:8081/CEmDcK"}
```

Returned code `CEmDcK`. It does not match `djm2he`.

## 7. Final stop

As in step 4, `kill -INT` did not end the process within 20 s. `taskkill /PID 17620 /F` ended it, and afterwards `tasklist` showed no `java.exe` processes.

## App logs

### Shutdown of the first instance (PID 21080)

The process was force-terminated with `taskkill /F`. It wrote no shutdown log lines. The last lines in its log are:

```
2026-11-12T00:29:55.388+05:30  INFO 21080 --- [url-shortener] [           main] c.m.u.UrlShortenerApplication            : Started UrlShortenerApplication in 2.136 seconds (process running for 2.709)
2026-11-12T00:29:56.055+05:30  INFO 21080 --- [url-shortener] [nio-8090-exec-1] o.a.c.c.C.[Tomcat].[localhost].[/]       : Initializing Spring DispatcherServlet 'dispatcherServlet'
2026-11-12T00:29:56.055+05:30  INFO 21080 --- [url-shortener] [nio-8090-exec-1] o.s.web.servlet.DispatcherServlet        : Initializing Servlet 'dispatcherServlet'
2026-11-12T00:29:56.055+05:30  INFO 21080 --- [url-shortener] [nio-8090-exec-1] o.s.web.servlet.DispatcherServlet        : Completed initialization in 0 ms
```

### Second startup (PID 17620)

```
 :: Spring Boot ::                (v4.1.2)

2026-11-12T00:30:24.516+05:30  INFO 17620 --- [url-shortener] [           main] c.m.u.UrlShortenerApplication            : Starting UrlShortenerApplication v0.0.1-SNAPSHOT using Java 21.0.7 with PID 17620 (E:\Projects\URL Shortener\url-shortener\target\url-shortener-0.0.1-SNAPSHOT.jar started by malin in E:\Projects\URL Shortener\url-shortener)
2026-11-12T00:30:24.521+05:30  INFO 17620 --- [url-shortener] [           main] c.m.u.UrlShortenerApplication            : No active profile set, falling back to 1 default profile: "default"
2026-11-12T00:30:26.696+05:30  INFO 17620 --- [url-shortener] [           main] o.s.boot.tomcat.TomcatWebServer          : Tomcat initialized with port 8090 (http)
2026-11-12T00:30:26.723+05:30  INFO 17620 --- [url-shortener] [           main] o.apache.catalina.core.StandardService   : Starting service [Tomcat]
2026-11-12T00:30:26.723+05:30  INFO 17620 --- [url-shortener] [           main] o.apache.catalina.core.StandardEngine    : Starting Servlet engine: [Apache Tomcat/11.0.27]
2026-11-12T00:30:26.809+05:30  INFO 17620 --- [url-shortener] [           main] b.w.c.s.WebApplicationContextInitializer : Root WebApplicationContext: initialization completed in 2167 ms
2026-11-12T00:30:27.454+05:30  INFO 17620 --- [url-shortener] [           main] o.s.boot.tomcat.TomcatWebServer          : Tomcat started on port 8090 (http) with context path '/'
2026-11-12T00:30:27.474+05:30  INFO 17620 --- [url-shortener] [           main] c.m.u.UrlShortenerApplication            : Started UrlShortenerApplication in 3.459 seconds (process running for 3.965)
2026-11-12T00:30:27.619+05:30  INFO 17620 --- [url-shortener] [nio-8090-exec-1] o.a.c.c.C.[Tomcat].[localhost].[/]       : Initializing Spring DispatcherServlet 'dispatcherServlet'
2026-11-12T00:30:27.620+05:30  INFO 17620 --- [url-shortener] [nio-8090-exec-1] o.s.web.servlet.DispatcherServlet        : Initializing Servlet 'dispatcherServlet'
2026-11-12T00:30:27.621+05:30  INFO 17620 --- [url-shortener] [nio-8090-exec-1] o.s.web.servlet.DispatcherServlet        : Completed initialization in 1 ms
```
