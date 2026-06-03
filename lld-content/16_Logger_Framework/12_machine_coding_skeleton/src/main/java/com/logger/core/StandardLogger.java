package com.logger.core;

import com.logger.api.Level;
import com.logger.api.Logger;
import com.logger.api.MDC;
import com.logger.appender.Appender;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

public final class StandardLogger implements Logger {

    private final String name;
    private final LoggerContext ctx;
    private final Clock clock;
    private final ThreadLocal<Boolean> reentrant = ThreadLocal.withInitial(() -> false);

    /** Cached on each config update; volatile so other threads see it. */
    volatile Level effectiveLevel = Level.INFO;
    volatile List<Appender> effectiveAppenders = List.of();

    StandardLogger(String name, LoggerContext ctx, Clock clock) {
        this.name = name; this.ctx = ctx; this.clock = clock;
    }

    @Override public String name()             { return name; }
    @Override public Level effectiveLevel()    { return effectiveLevel; }
    @Override public boolean isEnabled(Level l) { return l.rank() >= effectiveLevel.rank(); }

    @Override public void trace(String p, Object... a) { log(Level.TRACE, null, p, a); }
    @Override public void debug(String p, Object... a) { log(Level.DEBUG, null, p, a); }
    @Override public void info (String p, Object... a) { log(Level.INFO,  null, p, a); }
    @Override public void warn (String p, Object... a) { log(Level.WARN,  null, p, a); }
    @Override public void error(String p, Object... a) { log(Level.ERROR, null, p, a); }
    @Override public void error(Throwable t, String p, Object... a) { log(Level.ERROR, t, p, a); }

    private void log(Level level, Throwable t, String pattern, Object[] args) {
        if (!isEnabled(level)) return;
        if (Boolean.TRUE.equals(reentrant.get())) return;   // recursive log guard

        LogEvent evt = new LogEvent(
                clock.instant(),
                level,
                name,
                Thread.currentThread().getName(),
                MDC.snapshot(),
                pattern, args, t);

        reentrant.set(true);
        try {
            for (Appender a : effectiveAppenders) {
                a.append(evt);
            }
        } finally {
            reentrant.set(false);
        }
    }
}
