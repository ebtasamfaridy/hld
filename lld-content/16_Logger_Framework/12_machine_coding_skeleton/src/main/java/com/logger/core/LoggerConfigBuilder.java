package com.logger.core;

import com.logger.api.Level;
import com.logger.appender.Appender;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LoggerConfigBuilder {

    private final Map<String, LoggerConfig.Entry> entries = new HashMap<>();

    public LoggerConfigBuilder root(Level level, Appender... appenders) {
        entries.put("", new LoggerConfig.Entry(level, false, List.of(appenders)));
        return this;
    }

    public LoggerConfigBuilder logger(String name, Level level, boolean additive, Appender... appenders) {
        entries.put(name, new LoggerConfig.Entry(level, additive, List.of(appenders)));
        return this;
    }

    public LoggerConfig build() {
        if (!entries.containsKey("")) {
            throw new IllegalStateException("config must define a root logger");
        }
        return new LoggerConfig(entries);
    }
}
