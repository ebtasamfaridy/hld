package com.logger.core;

import com.logger.api.Level;

import java.time.Instant;
import java.util.Map;

/** Immutable record for one logged event. */
public final class LogEvent {

    private final Instant timestamp;
    private final Level level;
    private final String loggerName;
    private final String threadName;
    private final Map<String, String> mdc;
    private final String pattern;
    private final Object[] args;
    private final Throwable throwable;

    public LogEvent(Instant timestamp, Level level, String loggerName, String threadName,
                    Map<String, String> mdc, String pattern, Object[] args, Throwable throwable) {
        this.timestamp = timestamp; this.level = level; this.loggerName = loggerName;
        this.threadName = threadName; this.mdc = mdc; this.pattern = pattern;
        this.args = args; this.throwable = throwable;
    }

    public Instant timestamp()           { return timestamp; }
    public Level level()                 { return level; }
    public String loggerName()           { return loggerName; }
    public String threadName()           { return threadName; }
    public Map<String, String> mdc()     { return mdc; }
    public String pattern()              { return pattern; }
    public Object[] args()               { return args; }
    public Throwable throwable()         { return throwable; }

    /** Render the message by substituting `{}` placeholders with args (lazy). */
    public String renderMessage() {
        if (args == null || args.length == 0) return pattern;
        StringBuilder sb = new StringBuilder(pattern.length() + 16 * args.length);
        int i = 0, ai = 0;
        while (i < pattern.length()) {
            int idx = pattern.indexOf("{}", i);
            if (idx < 0 || ai >= args.length) { sb.append(pattern, i, pattern.length()); break; }
            sb.append(pattern, i, idx);
            sb.append(args[ai++]);
            i = idx + 2;
        }
        return sb.toString();
    }
}
