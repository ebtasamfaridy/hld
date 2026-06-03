package com.pubsub.storage;

import com.pubsub.domain.Record;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * In-memory append-only log for a single partition.
 * Single-writer (the leader). Many readers (consumers).
 */
public final class PartitionLog {

    private final List<Record> records = new ArrayList<>();
    private final ReentrantLock writeLock = new ReentrantLock();
    private final Clock clock;
    private long highWatermark = 0; // single-broker → HW == nextOffset after append

    public PartitionLog(Clock clock) { this.clock = clock; }

    public long append(String key, String value) {
        writeLock.lock();
        try {
            long offset = records.size();
            records.add(new Record(offset, clock.instant(), key, value));
            highWatermark = offset + 1;
            return offset;
        } finally {
            writeLock.unlock();
        }
    }

    /** Read up to maxRecords starting at fromOffset (inclusive); only up to HW. */
    public List<Record> read(long fromOffset, int maxRecords) {
        if (fromOffset < 0) fromOffset = 0;
        if (fromOffset >= highWatermark) return List.of();
        long upto = Math.min(highWatermark, fromOffset + maxRecords);
        List<Record> out = new ArrayList<>((int) (upto - fromOffset));
        for (long o = fromOffset; o < upto; o++) out.add(records.get((int) o));
        return out;
    }

    public long endOffset() { return highWatermark; }
    public long size() { return records.size(); }
}
