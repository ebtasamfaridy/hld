package com.logger.appender;

import com.logger.api.Level;
import com.logger.core.LogEvent;
import com.logger.layout.Layout;

import java.io.PrintStream;

public final class ConsoleAppender extends AbstractAppender {

    private final PrintStream out;
    private final PrintStream err;

    public ConsoleAppender(String name, Layout layout) {
        this(name, layout, System.out, System.err);
    }
    public ConsoleAppender(String name, Layout layout, PrintStream out, PrintStream err) {
        super(name, layout);
        this.out = out; this.err = err;
    }

    @Override
    protected void doAppend(LogEvent event, String formatted) {
        if (event.level().rank() >= Level.WARN.rank()) err.print(formatted);
        else                                            out.print(formatted);
    }
}
