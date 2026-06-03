package com.carrental.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Hour-aligned bucket. Represents a 1-hour window starting at the given epoch hour. */
public record HourBucket(long epochHour) implements Comparable<HourBucket> {

    public static HourBucket of(Instant t) {
        return new HourBucket(t.truncatedTo(ChronoUnit.HOURS).getEpochSecond() / 3600);
    }

    public Instant start() { return Instant.ofEpochSecond(epochHour * 3600); }
    public Instant end()   { return start().plus(1, ChronoUnit.HOURS); }
    public HourBucket next() { return new HourBucket(epochHour + 1); }

    @Override public int compareTo(HourBucket o) { return Long.compare(this.epochHour, o.epochHour); }
    @Override public String toString() { return start().toString(); }
}
