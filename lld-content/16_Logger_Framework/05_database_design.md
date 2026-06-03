# 05 · Logger Framework — Storage / Persistence

A logger framework has no database. But the **storage problem is the appender problem**.

## File appender

```
/var/log/app/
  app.log              ← active write target
  app.log.1.gz         ← rolled, compressed
  app.log.2.gz
  app.log.3.gz
  ...
```

Production-grade file appender:
- Buffered writer (e.g., 8 KB buffer) to amortize syscalls.
- Periodic flush (configurable: every N events or every M ms).
- Optional `fsync` policy: NEVER (default) / EVERY / EVERY_N_EVENTS.
- Atomic file roll: open new file, swap, close old.

## Rolling policies

### Size-based
When the active file exceeds `maxFileSize`, roll. Old files numbered `app.log.1`, `.2`, …, `.N` (oldest deleted).

### Time-based
Roll at fixed boundaries (`hourly`, `daily`). Filename pattern `app.%d{yyyy-MM-dd}.log`.

### Composite (size + time)
Roll on whichever triggers first. Common in production.

### Compression
Async post-roll: compress the closed file with gzip. Saves 5–10× space.

### Retention
Delete files older than N days, or keep total bytes under N GB.

## Console appender
Writes to `System.out` / `System.err`. Optional colorization via ANSI codes (TTY detection).

## Network appenders

### Syslog (UDP/TCP)
Format `RFC 5424` or `RFC 3164`. Lossy on UDP; lossless on TCP.

### HTTP
POST batches of events to an HTTP endpoint (e.g., a log shipper). Backoff on 5xx.

### Kafka
Publish each event as a Kafka record. Buffered batches; partitioned by `loggerName` or `requestId`.

## Audit / tamper-resistant appender
Appends a chained hash to each event:
```
event_n.hash = sha256(event_n.payload || event_(n-1).hash)
```
Auditors can replay the chain to detect tampering.

## In-memory ring buffer (for async)

The async appender's queue:

```
+-------------------------------+
| Single-producer or MPMC ring  |
| Power-of-two size, e.g. 8192  |
| Each cell holds a LogEvent ref|
| Producer index (atomic)       |
| Consumer index (atomic)       |
+-------------------------------+
```

LMAX Disruptor takes this much further: each producer claims a sequence via CAS, writes to the cell, publishes the sequence; consumer waits on the published sequence. False-sharing avoided via cache-line padding.

For our skeleton: `LinkedBlockingQueue` is fine; production-grade uses Disruptor.

## Configuration storage

### File
`logger.yaml` / `log4j2.xml` / `logback.xml` — declarative tree of loggers + appenders.

### Programmatic
Builder API at startup.

### Hot-reload
Two approaches:
1. **WatchService** on the config file. On change, re-parse and atomically swap.
2. **Periodic poll** at fixed interval.

Atomic swap means: build the entire new config tree off-thread, then `LoggerContext.config = newConfig` is one volatile write.

## Output

```
File:      buffered, periodic flush, atomic rolls (size/time/composite),
           compression, retention
Console:   System.out/err with TTY-detected colorization
Network:   Syslog (UDP/TCP), HTTP batching, Kafka
Audit:     hash-chained tamper-resistant appender (V2)
Async:     ring buffer (Disruptor in production; LinkedBlockingQueue for skeleton)
Config:    YAML/XML file → tree build → atomic volatile swap; hot-reload via WatchService
```
