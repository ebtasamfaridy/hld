package com.logger.core;

import com.logger.api.Level;
import com.logger.appender.Appender;

import java.util.List;
import java.util.Map;

public final class LoggerConfig {

    public static final class Entry {
        public final Level level;
        public final boolean additive;
        public final List<Appender> appenders;

        public Entry(Level level, boolean additive, List<Appender> appenders) {
            this.level = level;
            this.additive = additive;
            this.appenders = List.copyOf(appenders);
        }
    }

    /** loggerName → entry (root keyed on ""). */
    private final Map<String, Entry> entries;

    public LoggerConfig(Map<String, Entry> entries) {
        this.entries = Map.copyOf(entries);
    }

    public Entry forName(String name)   { return entries.get(name); }
    public Map<String, Entry> entries() { return entries; }
}
