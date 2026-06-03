package com.logger.appender;

import com.logger.core.LogEvent;
import com.logger.filter.Filter;
import com.logger.layout.Layout;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class AbstractAppender implements Appender {

    protected final String name;
    protected final Layout layout;
    protected final List<Filter> filters = new CopyOnWriteArrayList<>();
    protected volatile boolean started = false;

    protected AbstractAppender(String name, Layout layout) {
        this.name = name;
        this.layout = layout;
    }

    public AbstractAppender addFilter(Filter f) { filters.add(f); return this; }

    @Override public String name() { return name; }
    @Override public void start()  { started = true; }
    @Override public void close()  { started = false; }

    @Override
    public void append(LogEvent event) {
        if (!started) return;
        for (Filter f : filters) {
            Filter.Result r = f.decide(event);
            if (r == Filter.Result.DENY)  return;
            if (r == Filter.Result.ALLOW) break;
        }
        try {
            doAppend(event, layout.format(event));
        } catch (Exception e) {
            // Never crash the app on a logging error.
            System.err.println("[logger] appender '" + name + "' failed: " + e);
        }
    }

    protected abstract void doAppend(LogEvent event, String formatted);
}
