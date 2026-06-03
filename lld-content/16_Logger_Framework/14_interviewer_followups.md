# 14 · Logger Framework — Interviewer Follow-ups

## Q1. "Walk me through what happens when I call `log.info("user={}", id)`."

1. The logger checks `effectiveLevel <= INFO`. It's a volatile int compare; ~5 ns. If false, return.
2. Build a `LogEvent`: timestamp, level, logger name, thread name, MDC snapshot, pattern, args.
3. For each appender (and ancestor appenders if `additive=true`):
   a. Run filter chain; if DENY, skip.
   b. Layout formats the event into a string.
   c. Appender writes to its sink (stdout / file / queue).
4. Done.

The disabled-level path is what the framework optimizes for: it's the most common call.

---

## Q2. "Why is `log.info("x=" + x)` worse than `log.info("x={}", x)`?"

Because string concatenation **always runs**, even if INFO is disabled. The expression `"x=" + x` allocates a `StringBuilder`, calls `x.toString()`, builds a final string — all before the framework gets a chance to check the level.

Parameterized form passes `x` as a separate object reference. The framework checks the level first; if disabled, the args are never used. Zero allocation on the disabled path.

---

## Q3. "How does effective level resolution work?"

For logger `com.app.web.OrderController`:
1. Look up `com.app.web.OrderController` in config — has explicit level? Use it.
2. Else look up `com.app.web` — has explicit level? Use it.
3. Else `com.app`. Else root.

Cache the result on the logger (volatile int). On config reload, walk all loggers and recompute.

---

## Q4. "Why hierarchical loggers in the first place?"

Two big wins:
1. **Bulk configuration**: set `com.app.dao=DEBUG` and every DAO class inherits it.
2. **Additive appenders**: attach a single console appender to root; every logger writes to it without per-logger config.

Without hierarchy, every logger would need explicit configuration. Painful at scale.

---

## Q5. "What's MDC and why do you need it?"

MDC = Mapped Diagnostic Context. A per-thread map of contextual fields (requestId, userId, tenantId).

Without MDC, you'd thread these through every method signature or repeat them on every log call. With MDC, you put them once at request start; every log event automatically captures and includes them.

Implementation: `ThreadLocal<Map<String,String>>`. Snapshot taken at `LogEvent` build time so async appenders see the values from when the log call happened, not from when the drain thread reads.

---

## Q6. "What goes wrong with MDC and thread pools?"

When a request thread offloads work to an executor, the worker thread has its own `ThreadLocal` — different MDC (typically empty).

Fix: capture the MDC on the request thread, restore it on the worker thread.

```java
Map<String,String> ctx = MDC.snapshot();
executor.submit(() -> {
    var prev = MDC.snapshot();
    MDC.clear(); ctx.forEach(MDC::put);
    try { doWork(); }
    finally { MDC.clear(); prev.forEach(MDC::put); }
});
```

SLF4J has `MDCContext` helpers; production should use them.

---

## Q7. "Async logging — what's the ring buffer doing?"

The caller writes a `LogEvent` to a queue (`BlockingQueue` in our skeleton, LMAX Disruptor in production). A single drain thread reads from the queue and runs the underlying appender.

Caller cost: ~100 ns enqueue. Producer is decoupled from disk speed. The drain thread amortizes I/O across batches.

When the queue is full, we either BLOCK (lossless, slows the caller) or DROP (lossy, throughput preserved). Configured per appender.

---

## Q8. "What if the underlying appender throws while async?"

The drain thread catches the exception, reports via the internal status logger, continues. The log is lost; the caller already returned successfully.

This is unavoidable in async mode — the caller already moved on. Tradeoff for performance.

---

## Q9. "Recursive logging — how do you prevent it?"

Imagine: a custom appender calls some method that itself logs. That log goes through the same appender → calls the same method → infinite recursion.

Mitigation: a `ThreadLocal<Boolean>` reentrant guard on the logger. On entry, check; if already set, route to a safe "internal status" channel instead.

In our skeleton, `StandardLogger` has the guard.

---

## Q10. "How does config hot-reload work without dropping in-flight logs?"

1. Build the new `LoggerConfig` tree off-thread.
2. Atomically swap the volatile `LoggerContext.config` reference.
3. Walk loggers, recompute their effective level + appenders (also volatile writes).

A log call in flight either uses the old config (if it captured the appender list before the swap) or the new (if after). Both are correct; nothing is half-applied.

Old appenders that are no longer referenced get `close()`d. New ones get `start()`ed.

---

## Q11. "What's the purpose of `additive=false`?"

Default behavior: a logger writes to its own appenders + ancestor appenders. So a `com.app.web` log line appears on root's console too.

For some loggers — say, an audit log going only to a tamper-proof sink — you don't want it duplicated to console. Set `additive=false` and only that logger's own appenders get it.

---

## Q12. "I configured my logger and nothing prints. How do I debug?"

Common causes:
1. Level is too high (e.g., logger is at WARN, you're calling INFO).
2. Logger has no appenders and `additive=false` cut off ancestors.
3. A filter is denying the events.
4. Appender failed to start (file path bad, port taken).
5. Async queue is full + drop policy.

Always have an internal status logger that emits framework events to stderr. That's how you find #4 and #5 in production.

---

## Q13. "A user reports DEBUG logs appearing in prod. How would you investigate?"

Check the config tree:
- Was a package set to DEBUG via override (env var, system property)?
- Is there an inherited DEBUG from a parent?
- Was hot-reload applied recently?

The framework's effective level is the first non-null walking up; if any parent has DEBUG, children inherit. Use the admin endpoint to enumerate effective levels per logger and find the culprit.

---

## Q14. "Why immutable LogEvent?"

Multiple appenders consume the same event in parallel (especially in async, where the producer thread already returned). Mutable shared state would race.

Immutable means: build once on the calling thread, share freely. No synchronization needed for reads. The MDC snapshot is also a defensive copy.

---

## Q15. "How would you test this?"

- **Unit**: each layout formats correctly; each filter decides correctly; each appender writes correctly.
- **Hierarchy**: configure a tree; assert effective levels; assert additive vs non-additive distribution.
- **Concurrency**: 32 threads × 1 M `info()` calls; assert no exception, no torn output, line count == call count for each appender (modulo drops in async).
- **Async correctness**: producer-consumer race; shutdown drain finishes within deadline.
- **MDC**: thread sees its own MDC; threadpool propagation manual test.
- **Hot-reload**: change config mid-flight; assert old + new behavior is correct on either side of swap.

---

## Output

```
Drilled:
- Hot path: level check → event build → fan-out
- Why parameterized API (lazy formatting)
- Effective level resolution + caching
- Why hierarchy
- MDC purpose; threadpool gotcha
- Async ring buffer + drop/block policies
- Recursive log guard
- Hot-reload atomicity
- additive=false semantics
- Debugging silence
- Investigating leaked DEBUG
- Immutable LogEvent rationale
- Test strategy
```
