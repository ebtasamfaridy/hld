package com.logger.core;

import com.logger.api.Level;
import com.logger.api.Logger;
import com.logger.appender.Appender;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class LoggerContext {

    private final ConcurrentMap<String, StandardLogger> registry = new ConcurrentHashMap<>();
    private final Clock clock;
    private volatile LoggerConfig config;

    public LoggerContext(LoggerConfig initial, Clock clock) {
        this.clock = clock;
        reload(initial);
    }
    public LoggerContext(LoggerConfig initial) { this(initial, Clock.systemUTC()); }

    public Logger getLogger(String name) {
        return registry.computeIfAbsent(name, n -> {
            StandardLogger l = new StandardLogger(n, this, clock);
            applyConfig(l);
            return l;
        });
    }
    public Logger getLogger(Class<?> cls) { return getLogger(cls.getName()); }

    public synchronized void reload(LoggerConfig newConfig) {
        this.config = newConfig;
        for (StandardLogger l : registry.values()) applyConfig(l);
    }

    private void applyConfig(StandardLogger l) {
        // Walk parents to determine effective level + appenders.
        Level lvl = null;
        boolean stopAdditive = false;
        List<Appender> apps = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        String name = l.name();
        while (true) {
            LoggerConfig.Entry entry = config.forName(name);
            if (entry != null) {
                if (lvl == null) lvl = entry.level;
                for (Appender a : entry.appenders) {
                    if (seen.add(a.name())) apps.add(a);
                }
                // If the *first* matching entry says additive=false, stop walking.
                if (!entry.additive) { stopAdditive = true; break; }
            }
            if (name.isEmpty()) break;
            int dot = name.lastIndexOf('.');
            name = (dot < 0) ? "" : name.substring(0, dot);
        }
        if (!stopAdditive) {
            // Always include root (already covered above unless additive cut us off).
        }
        l.effectiveLevel = (lvl != null) ? lvl : Level.INFO;
        l.effectiveAppenders = List.copyOf(apps);
    }
}
