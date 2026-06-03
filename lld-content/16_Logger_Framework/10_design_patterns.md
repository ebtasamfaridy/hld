# 10 · Logger Framework — Design Patterns

## 1. Strategy — `Appender`, `Layout`, `Filter`
Three independent dimensions. Pluggable per logger.

## 2. Decorator — `AsyncAppender`
Wraps any `Appender` and changes behavior (sync→async) without changing the interface. `LevelMappingAppender`, `RetryAppender`, `RateLimitedAppender` follow the same pattern.

## 3. Composite — Logger hierarchy
Each logger has a parent; a logger is composed of itself + (recursively) its parents' appenders when `additive=true`.

## 4. Chain of Responsibility — Filters
Filters chained; each returns ALLOW / DENY / NEUTRAL. First non-NEUTRAL stops the chain.

## 5. Factory + Registry — `LoggerFactory.getLogger(name)`
Returns the cached logger for the name; creates one if absent.

## 6. Builder — `LoggerConfigBuilder`
Fluent declarative config when not using a file.

## 7. Singleton — `LoggerContext` (per JVM)
There's typically one context per process. Loaded lazily on first `getLogger`.

## 8. Memento — `LogEvent` carries an MDC snapshot
The event captures the thread's MDC at creation; later async processing sees the snapshot, not the now-different thread state.

## 9. Observer — internal status logger
Configuration changes, appender errors, dropped events all emit status events. A status listener can write them to stderr or a separate file.

## 10. Object pool / cache-line aligned ring buffer (advanced)
LMAX Disruptor pre-allocates cells; producer/consumer publish/wait on sequences. Avoids GC pressure on hot path.

## 11. Template method — base class for layouts
`AbstractLayout.format(evt)` does the common timestamp/level header; subclasses fill the body. Optional; many implementations skip the abstract base.

## What we explicitly avoid

| Pattern | Why not |
| --- | --- |
| String concatenation in caller | `log.debug("x=" + x)` allocates even when DEBUG off; use `log.debug("x={}", x)` |
| Synchronized appender for high throughput | Use AsyncAppender or per-thread buffering |
| Reflection-based message formatting | Slow; use simple `{}` substitution |
| Re-formatting LogEvent for each appender | Format once if shared; let async handle the timing |
| Crashing app on logging error | Always swallow + report via internal status |

## Pattern table

| Pattern | Where | What it solves |
| --- | --- | --- |
| Strategy | Appender / Layout / Filter | Pluggable behavior on three independent axes |
| Decorator | AsyncAppender, RateLimited | Wrap to add behavior without subclassing |
| Composite | Logger hierarchy | Inherit config + additive appenders from parents |
| Chain of Responsibility | Filter chain | Multiple decisions; short-circuit |
| Factory + Registry | LoggerFactory | Cached logger lookup |
| Builder | LoggerConfigBuilder | Programmatic config |
| Memento | MDC snapshot in LogEvent | Capture context at log time |
| Object pool | Ring buffer cells (Disruptor) | Avoid GC on hot path |

## Output

The framework is **Strategy along three axes (Appender, Layout, Filter) + Composite hierarchy + Decorator for async**. Master those four patterns and the rest is plumbing.
