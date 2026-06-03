# 06 · Logger Framework — API Design

The framework is a **library**. The relevant APIs are Java method signatures.

## Logger API

```java
public interface Logger {
    boolean isTraceEnabled();
    boolean isDebugEnabled();
    boolean isInfoEnabled();
    boolean isWarnEnabled();
    boolean isErrorEnabled();

    void trace(String pattern, Object... args);
    void debug(String pattern, Object... args);
    void info(String pattern, Object... args);
    void warn(String pattern, Object... args);
    void error(String pattern, Object... args);

    void error(String pattern, Throwable t, Object... args);
    void error(Throwable t, String pattern, Object... args);

    String name();
    Level effectiveLevel();
}
```

### Decisions

- **Parameterized messages** (`pattern + Object... args`) — the foundation of lazy formatting.
- **Throwable as a separate parameter** (rather than as an arg) — makes it explicit and avoids accidental concatenation.
- **`isXxxEnabled()`** — for cases where building the message is expensive (e.g., huge JSON serialization). The fast path inside `info(...)` already does this check; the public method is for when the *caller* wants to skip work.
- **Returns `Level` not `int`** — type-safe.

## Factory

```java
public final class LoggerFactory {
    public static Logger getLogger(Class<?> cls);
    public static Logger getLogger(String name);
    public static LoggerContext getContext();   // for advanced use
}
```

### Decisions

- **Class overload** as syntactic sugar: `getLogger(OrderService.class)` → name = `"com.app.OrderService"`. By far the most common call site.

## MDC API

```java
public final class MDC {
    public static void put(String key, String value);
    public static String get(String key);
    public static void remove(String key);
    public static Map<String,String> getCopyOfContextMap();
    public static void setContextMap(Map<String,String> map);
    public static void clear();

    /** Try-with-resources convenience. */
    public static AutoCloseable closeable(String key, String value);
}
```

The `closeable` overload gives:
```java
try (var ignored = MDC.closeable("requestId", id)) {
   // do work; logs include requestId; auto-cleared on close
}
```

## Configuration API

```java
public final class LoggerConfigBuilder {
    public LoggerConfigBuilder root(Level level, Appender... appenders);
    public LoggerConfigBuilder logger(String name, Level level, boolean additive, Appender... appenders);
    public LoggerConfig build();
}

public final class LoggerContext {
    public static LoggerContext create(LoggerConfig config);
    public void reload(LoggerConfig newConfig);
    public Logger getLogger(String name);
}
```

## Appender / Layout / Filter contracts

```java
public interface Appender extends AutoCloseable {
    void append(LogEvent event);
    void start();
    @Override void close();
    String name();
}

public interface Layout {
    String format(LogEvent event);
}

public interface Filter {
    enum Result { ALLOW, DENY, NEUTRAL }
    Result decide(LogEvent event);
}
```

## Errors

| Class | When |
| --- | --- |
| `LoggingException` | Configuration error or appender start failure |
| (silent) | Runtime appender errors are caught and reported via the internal status logger |

We never let a logging failure propagate into the application. The whole point of logging is to be unobtrusive.

## Output

```
Logger:       parameterized messages + throwable; isXxxEnabled fast checks
Factory:      LoggerFactory.getLogger(cls / name); cached
MDC:          ThreadLocal map + try-with-resources closeable
Config:       Builder + LoggerContext.reload for hot-swap
Appender API: append(evt) + lifecycle (start/close)
Layout/Filter: simple SAM interfaces
Errors:       silent on append; LoggingException at configuration time
```
