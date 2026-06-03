# 07 · Logger Framework — Class Diagrams

## Core class diagram

```mermaid
classDiagram
    class Logger {
      <<interface>>
      +trace/debug/info/warn/error(pattern, args...)
      +isXxxEnabled() boolean
      +name() string
    }

    class StandardLogger {
      -name: string
      -context: LoggerContext
      -parent: StandardLogger
      -level: Level
      -appenders: List~Appender~
      -additive: boolean
      -effectiveLevel: int (cached, volatile)
      +log(level, pattern, args, t)
    }
    Logger <|.. StandardLogger

    class LoggerContext {
      -root: StandardLogger
      -registry: ConcurrentMap~String, StandardLogger~
      -config: volatile LoggerConfig
      +getLogger(name) Logger
      +reload(config)
    }

    class Level {
      <<enum>>
      TRACE INFO DEBUG WARN ERROR FATAL OFF
      +rank() int
    }

    class LogEvent {
      -timestampNanos: long
      -level: Level
      -loggerName: string
      -threadName: string
      -mdcSnapshot: Map
      -pattern: string
      -args: Object[]
      -throwable: Throwable
      +renderMessage() string
    }

    class Appender {
      <<interface>>
      +append(evt)
      +start() / close()
      +name() string
    }
    class ConsoleAppender
    class FileAppender
    class AsyncAppender {
      -wrapped: Appender
      -ringBuffer: BlockingQueue
      -drainThread: Thread
      -dropOnFull: boolean
    }
    Appender <|.. ConsoleAppender
    Appender <|.. FileAppender
    Appender <|.. AsyncAppender
    AsyncAppender o-- Appender : wraps

    class Layout {
      <<interface>>
      +format(evt) string
    }
    class PatternLayout
    class JsonLayout
    Layout <|.. PatternLayout
    Layout <|.. JsonLayout

    class Filter {
      <<interface>>
      +decide(evt) Result
    }
    class LevelFilter
    class RegexFilter
    Filter <|.. LevelFilter
    Filter <|.. RegexFilter

    class MDC {
      <<utility>>
      +put/get/remove/clear
      +closeable(k, v) AutoCloseable
    }

    class LoggerFactory {
      +getLogger(cls/name) Logger
    }

    LoggerContext o-- StandardLogger
    StandardLogger o-- Appender
    Appender o-- Layout
    Appender o-- Filter
    LoggerFactory ..> LoggerContext
```

## Package layout (`com.logger`)

```
api/        Logger, LoggerFactory, MDC, Level
core/       LoggerContext, StandardLogger, LogEvent, LoggerConfig, LoggerConfigBuilder
appender/   Appender, ConsoleAppender, FileAppender, AsyncAppender
layout/     Layout, PatternLayout, JsonLayout
filter/     Filter, LevelFilter, RegexFilter
async/      RingBuffer (or BlockingQueue based)
config/     ConfigLoader (V2: YAML/XML)
```

## Why these abstractions

### `Logger` as an interface
Different implementations: `StandardLogger`, `NopLogger` (for tests), `BridgeLogger` (delegates to another framework). All compile-time interchangeable.

### `LoggerContext` as the registry
Centralizes the tree, the config, and the lookup cache. `getLogger(name)` returns the same instance for the same name (via `ConcurrentMap.computeIfAbsent`).

### `Appender` as a Strategy
Plug different sinks. The same `LogEvent` can be appended by N appenders.

### `Layout` separate from `Appender`
A `FileAppender` can use any `Layout`. A `PatternLayout` can be used by any appender. This Cartesian product is exactly what Strategy is for.

### `Filter` chain
Allows fine-grained policies without subclassing. Order matters; first non-NEUTRAL decision wins.

### `AsyncAppender` as a Decorator
Wraps any appender to make it async. The user sees the same `Appender` interface; behavior is buffered.

### `LogEvent` as a value object
Immutable; built once on the calling thread; consumed by many appenders. Critical to avoid races when async appenders share the same event reference across threads.

## Output

```
Layered:    LoggerFactory → LoggerContext → Logger → Appenders → Layouts → Filters
Strategy:   Appender, Layout, Filter
Decorator:  AsyncAppender wraps any Appender
Utility:    MDC ThreadLocal, LoggerFactory
Immutable:  LogEvent (built once, shared safely across appenders)
```
