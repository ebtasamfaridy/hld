package com.logger.api;

public interface Logger {
    String name();
    Level effectiveLevel();

    boolean isEnabled(Level level);

    default boolean isTraceEnabled() { return isEnabled(Level.TRACE); }
    default boolean isDebugEnabled() { return isEnabled(Level.DEBUG); }
    default boolean isInfoEnabled()  { return isEnabled(Level.INFO);  }
    default boolean isWarnEnabled()  { return isEnabled(Level.WARN);  }
    default boolean isErrorEnabled() { return isEnabled(Level.ERROR); }

    void trace(String pattern, Object... args);
    void debug(String pattern, Object... args);
    void info(String pattern, Object... args);
    void warn(String pattern, Object... args);
    void error(String pattern, Object... args);

    void error(Throwable t, String pattern, Object... args);
}
