# 12 · Logger Framework — Machine Coding Skeleton

In-process logger with hierarchical loggers, multiple appenders, layouts, filters, MDC, and an async wrapper.

```
src/main/java/com/logger/
├── api/         Logger (interface), LoggerFactory, MDC, Level
├── core/        StandardLogger, LoggerContext, LogEvent, LoggerConfig, LoggerConfigBuilder
├── appender/    Appender, ConsoleAppender, FileAppender, AsyncAppender
├── layout/      Layout, PatternLayout, JsonLayout
├── filter/      Filter, LevelFilter, RegexFilter
├── async/       (async wrapper uses java.util.concurrent BlockingQueue)
├── config/      (placeholder for V2 YAML loader)
└── Main.java
```

## Demo

1. Build context with root=INFO console + `com.app=DEBUG` json-file (additive=false).
2. Set MDC fields, log at INFO/DEBUG/WARN with parameters.
3. Show level filtering: DEBUG suppressed at root, allowed under `com.app`.
4. Wrap file appender in `AsyncAppender`; show non-blocking enqueue.
5. Reload config to disable DEBUG; show that subsequent calls skip.
