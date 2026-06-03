package com.scheduler.trigger;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Minimal cron-like trigger. Supports a fixed-period expression of the form:
 *   "*\/N s"  fire every N seconds
 *   "*\/N m"  fire every N minutes
 * For interview demos. A real implementation parses the 5-/6-field cron grammar.
 */
public final class CronTrigger implements Trigger {

    private final Duration period;
    private final String expression;

    public CronTrigger(String expression) {
        this.expression = expression;
        String[] parts = expression.trim().split("\\s+");
        if (parts.length != 2 || !parts[0].startsWith("*/")) {
            throw new IllegalArgumentException("Use '*/N s' or '*/N m'");
        }
        long n = Long.parseLong(parts[0].substring(2));
        this.period = switch (parts[1]) {
            case "s" -> Duration.ofSeconds(n);
            case "m" -> Duration.ofMinutes(n);
            default  -> throw new IllegalArgumentException("unit s|m");
        };
    }

    @Override
    public Optional<Instant> nextFireTime(Instant previous, Instant now) {
        Instant base = (previous == null) ? alignUp(now, period) : previous.plus(period);
        if (base.isBefore(now)) base = alignUp(now, period);
        return Optional.of(base);
    }

    private static Instant alignUp(Instant now, Duration p) {
        long ms = now.toEpochMilli();
        long pms = p.toMillis();
        return Instant.ofEpochMilli(((ms + pms - 1) / pms) * pms);
    }

    @Override public String describe() { return "Cron[" + expression + "]"; }
}
