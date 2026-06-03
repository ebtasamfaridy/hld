package com.logger.appender;

import com.logger.core.LogEvent;

public interface Appender extends AutoCloseable {
    String name();
    void start();
    void append(LogEvent event);
    @Override void close();
}
