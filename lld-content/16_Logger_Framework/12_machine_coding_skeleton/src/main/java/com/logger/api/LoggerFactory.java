package com.logger.api;

import com.logger.core.LoggerConfig;
import com.logger.core.LoggerConfigBuilder;
import com.logger.core.LoggerContext;
import com.logger.appender.ConsoleAppender;
import com.logger.layout.PatternLayout;

public final class LoggerFactory {

    private static volatile LoggerContext context;

    private LoggerFactory() {}

    /** Lazily initializes a default context (root=INFO, console PatternLayout). */
    public static synchronized void install(LoggerContext ctx) { context = ctx; }

    public static LoggerContext context() {
        LoggerContext c = context;
        if (c == null) {
            synchronized (LoggerFactory.class) {
                if (context == null) {
                    ConsoleAppender def = new ConsoleAppender("default", new PatternLayout());
                    def.start();
                    LoggerConfig cfg = new LoggerConfigBuilder()
                            .root(Level.INFO, def)
                            .build();
                    context = new LoggerContext(cfg);
                }
                c = context;
            }
        }
        return c;
    }

    public static Logger getLogger(String name)   { return context().getLogger(name); }
    public static Logger getLogger(Class<?> cls)  { return context().getLogger(cls); }
}
