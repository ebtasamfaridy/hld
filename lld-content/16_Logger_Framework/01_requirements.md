# 01 · Logger Framework — Requirements

## Functional requirements

### Core
- Get a logger by name: `Logger log = LoggerFactory.getLogger("com.app.OrderService")`
- Log methods per level: `trace / debug / info / warn / error`
- Parameterized messages: `log.info("user={} order={}", userId, orderId)` — formatting only if level enabled.
- **Hierarchical loggers**: `com.app.OrderService` inherits config from `com.app`, then root.
- **Multiple appenders per logger** (additive by default; can opt out of inheriting parent appenders).
- **Pluggable layouts**: pattern (e.g., `%d %-5p [%t] %c - %m%n`), JSON, plain.
- **Filters** at logger or appender level (allow / deny).
- **MDC (Mapped Diagnostic Context)** — per-thread map of contextual fields.
- **Async logging** — opt-in, with ring-buffer queue + drain thread.
- **Configuration** via file + runtime reload.

### V2 extensions
- **Rolling file appender** — size-based, time-based.
- **Log sampling** for noisy levels (e.g., 1% of DEBUG in prod).
- **Structured logging** — emit log events as JSON for log aggregators.
- **Network appender** — HTTP, Kafka, Syslog.
- **Auditing appender** — tamper-resistant log for compliance.

## Out of scope

- Log aggregation (that's Splunk / ELK).
- Distributed tracing (separate concern; we only carry correlation IDs).
- Metrics (Prometheus / Micrometer).

## Non-functional

| NFR | Target | Why |
| --- | --- | --- |
| Disabled-level call cost | < 5 ns | `log.debug(...)` should be ~zero-cost when DEBUG is off |
| Enabled-level call cost (sync) | < 1 µs | Layout + I/O in the calling thread |
| Async enqueue cost | < 100 ns | LMAX-style ring buffer |
| Thread-safety | full | Multiple threads write the same logger concurrently |
| No log loss (default mode) | yes | Sync writes are durable |
| No log loss on JVM crash (async mode) | best-effort | Ring buffer drained on shutdown hook |
| Hot-reload of config | yes | Without restart |

## Actors

```
Application code   - calls log.info(...) etc.
Logger             - the front-end object the app holds
LoggerContext      - holds the logger hierarchy + config
Appender           - sink that writes a LogEvent
Layout             - formats LogEvent → bytes
Filter             - allow / deny LogEvent
MDC                - per-thread context map
ConfigLoader       - reads XML/JSON config, builds the tree
```

## Edge cases

| Case | Handling |
| --- | --- |
| Disabled level | Skip *before* parameter formatting (lazy) |
| Async queue full | Block (lossless) or drop (lossy) — configurable |
| Appender throws | Log to "internal status logger"; do not crash the app |
| Recursive logging | Detect (a logger appender that logs) and break the loop |
| Logger asked for the same name twice | Same instance returned; loggers cached |
| Config reload while logging in flight | Old loggers keep their old config until they re-resolve |
| Exception logging | Full stack trace + cause chain + suppressed exceptions |
| Very large messages (e.g., huge SQL) | Truncate at configured size with marker |
| Appender file disk full | Internal logger error; framework keeps running |
| Process kill | Shutdown hook drains async queue (best effort) |

## V1 vs V2

| Feature | V1 | V2 |
| --- | --- | --- |
| Hierarchical loggers + levels | ✓ | |
| Console + File appenders | ✓ | |
| Pattern layout | ✓ | |
| MDC | ✓ | |
| Filter chain | ✓ | |
| Async appender wrapper | ✓ | |
| Programmatic config | ✓ | |
| File-based config + hot-reload | | ✓ |
| Rolling file (size / time) | | ✓ |
| JSON / structured layout | | ✓ |
| Network / Kafka appenders | | ✓ |
| Sampling | | ✓ |
| LMAX Disruptor | | ✓ |

## Output

```
FR:    hierarchical loggers; per-logger level + appenders + filters;
       parameterized lazy messages; MDC; multiple appenders; async opt-in
NFR:   <5ns disabled call; <1µs enabled sync; <100ns async enqueue;
       thread-safe; no log loss in default sync mode
Edge:  level-disabled fast path, async queue full, appender errors,
       recursive logging, config hot-reload
```
