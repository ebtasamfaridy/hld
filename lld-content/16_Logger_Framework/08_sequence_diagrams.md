# 08 · Logger Framework — Sequence Diagrams

## 1. log.info("user={}", userId) — synchronous, level enabled

```mermaid
sequenceDiagram
    autonumber
    participant App as Application
    participant Lg as Logger (com.app.Order)
    participant Ev as LogEvent
    participant F as Filter chain
    participant Cs as ConsoleAppender
    participant Pl as PatternLayout
    participant Out as System.out

    App->>Lg: info("user={}", 42)
    Lg->>Lg: isInfoEnabled()? cachedLevel <= INFO → yes
    Lg->>Ev: build (timestamp, MDC snapshot, pattern, args)
    Lg->>F: decide(evt)
    F-->>Lg: ALLOW
    Lg->>Cs: append(evt)
    Cs->>Pl: format(evt)
    Pl->>Pl: render pattern
    Pl->>Pl: substitute args
    Pl->>Pl: serialize MDC
    Pl-->>Cs: bytes
    Cs->>Out: write(bytes)
```

## 2. log.debug(...) — level disabled (the fast path)

```mermaid
sequenceDiagram
    autonumber
    participant App
    participant Lg

    App->>Lg: debug("expensive {}", buildString())
    Note right of App: parameterized API: buildString()<br/>still runs (it's an arg eval).<br/>Use isDebugEnabled() to skip if needed.
    Lg->>Lg: cachedLevel = INFO, INFO > DEBUG → return
    Note over Lg: ~5ns, no LogEvent built,<br/>no formatting.
```

## 3. Async append flow

```mermaid
sequenceDiagram
    autonumber
    participant App
    participant Lg as Logger
    participant As as AsyncAppender
    participant RB as RingBuffer
    participant Dr as Drain thread
    participant FA as FileAppender
    participant Pl as JsonLayout
    participant Disk

    App->>Lg: info(...)
    Lg->>As: append(evt)
    As->>RB: enqueue(evt)
    As-->>Lg: return  (caller done, ~100ns)
    Note over Dr: separate thread
    Dr->>RB: dequeue batch
    Dr->>FA: append(evt) for each
    FA->>Pl: format(evt)
    Pl-->>FA: json bytes
    FA->>Disk: write
```

## 4. Hierarchy: log to com.app.web; root has console appender (additive)

```mermaid
sequenceDiagram
    autonumber
    participant Lg as com.app.web (level=null, additive=true)
    participant P1 as com.app   (level=DEBUG, has FileAppender)
    participant Rt as root      (level=INFO, has ConsoleAppender)

    Lg->>Lg: isEnabled(INFO)? walk parents → effective DEBUG → yes
    Lg->>Lg: collect appenders walking up:
    Lg->>P1: append(evt)  → FileAppender
    Lg->>Rt: append(evt)  → ConsoleAppender
```

If `additive=false` was set on `com.app.web`, the walk stops at it: only its own appenders.

## 5. Filter chain: deny

```mermaid
sequenceDiagram
    autonumber
    participant Lg as Logger
    participant F1 as RegexFilter (deny "/health")
    participant F2 as LevelFilter (allow >= WARN)
    participant Cs as Appender

    Lg->>F1: decide(evt with msg="GET /health 200")
    F1-->>Lg: DENY
    Note over Lg: chain short-circuits —<br/>F2 not consulted, Cs not called.
```

## 6. Configuration hot-reload

```mermaid
sequenceDiagram
    autonumber
    participant W as WatchService
    participant CL as ConfigLoader
    participant CB as LoggerConfigBuilder
    participant Ctx as LoggerContext
    participant Lgs as All Loggers

    W->>CL: file changed: logger.yaml
    CL->>CB: parse → new LoggerConfig
    CL->>Ctx: reload(newConfig)
    Ctx->>Ctx: stop appenders no longer referenced
    Ctx->>Ctx: start new appenders
    Ctx->>Lgs: invalidate effectiveLevel cache (atomic walk)
    Note over Ctx: in-flight log events use OLD config —<br/>new events use NEW.
```

## 7. Async queue full — drop policy

```mermaid
sequenceDiagram
    autonumber
    participant App
    participant As as AsyncAppender (dropOnFull=true)
    participant RB as RingBuffer (full)
    participant SI as StatusLogger

    App->>As: append(evt)
    As->>RB: tryOffer(evt)
    RB-->>As: false (full)
    As->>SI: warn("dropped event") [rate-limited]
    As-->>App: return (lossy: event lost)
```

In **block-on-full** mode the producer blocks on `put` instead. Lossless but slows the app.

## Output

```
Sync enabled:    level check → build LogEvent → filter → appenders → layout → write
Sync disabled:   level check → return (no work)
Async:           caller enqueues; drain thread does I/O
Hierarchy:       walk parents collecting appenders unless additive=false
Filter:          short-circuit on first non-NEUTRAL
Hot-reload:      build new tree off-thread; atomic volatile swap
Queue full:      drop (lossy) or block (lossless), configured per appender
```
