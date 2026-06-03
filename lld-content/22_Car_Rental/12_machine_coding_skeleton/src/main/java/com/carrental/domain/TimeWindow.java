package com.carrental.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record TimeWindow(Instant start, Instant end) {

    public TimeWindow {
        if (!end.isAfter(start)) throw new IllegalArgumentException("end must be after start");
    }

    public Duration duration() { return Duration.between(start, end); }
    public long hours() { return Math.max(1, duration().toHours()); }

    /** Inclusive list of hour buckets covering this window. */
    public List<HourBucket> hourBuckets() {
        List<HourBucket> out = new ArrayList<>();
        HourBucket b = HourBucket.of(start);
        HourBucket last = HourBucket.of(end.minusSeconds(1)); // exclusive end
        while (b.compareTo(last) <= 0) {
            out.add(b);
            b = b.next();
        }
        return out;
    }

    public boolean overlaps(TimeWindow o) {
        return this.start.isBefore(o.end) && o.start.isBefore(this.end);
    }
}
