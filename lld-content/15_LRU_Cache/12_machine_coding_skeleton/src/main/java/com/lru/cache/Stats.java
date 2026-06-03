package com.lru.cache;

import java.util.concurrent.atomic.LongAdder;

public final class Stats {
    private final LongAdder hits        = new LongAdder();
    private final LongAdder misses      = new LongAdder();
    private final LongAdder evictions   = new LongAdder();
    private final LongAdder loadsOk     = new LongAdder();
    private final LongAdder loadsFailed = new LongAdder();

    public void hit()       { hits.increment(); }
    public void miss()      { misses.increment(); }
    public void evict()     { evictions.increment(); }
    public void loadOk()    { loadsOk.increment(); }
    public void loadFail()  { loadsFailed.increment(); }

    public long hits()      { return hits.sum(); }
    public long misses()    { return misses.sum(); }
    public long evictions() { return evictions.sum(); }
    public double hitRatio() {
        long h = hits(), m = misses();
        return (h + m) == 0 ? 0.0 : (double) h / (h + m);
    }

    @Override
    public String toString() {
        return "Stats{hits=" + hits() + ", misses=" + misses()
                + ", evictions=" + evictions()
                + ", hitRatio=" + String.format("%.2f", hitRatio()) + "}";
    }
}
