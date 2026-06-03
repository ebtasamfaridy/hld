package com.logger.appender;

import com.logger.core.LogEvent;
import com.logger.layout.Layout;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Decorates an underlying appender; caller pays only the enqueue cost. */
public final class AsyncAppender extends AbstractAppender {

    private final Appender wrapped;
    private final BlockingQueue<LogEvent> queue;
    private final boolean dropOnFull;
    private final AtomicLong dropped = new AtomicLong();
    private Thread drain;
    private volatile boolean running = false;

    public AsyncAppender(String name, Appender wrapped, int queueSize, boolean dropOnFull) {
        super(name, /* layout unused; wrapped owns it */ event -> "");
        this.wrapped = wrapped;
        this.queue = new ArrayBlockingQueue<>(queueSize);
        this.dropOnFull = dropOnFull;
    }

    @Override
    public synchronized void start() {
        super.start();
        wrapped.start();
        running = true;
        drain = new Thread(this::drainLoop, "async-appender-" + name);
        drain.setDaemon(true);
        drain.start();
    }

    @Override
    public void append(LogEvent event) {
        if (!started) return;
        if (dropOnFull) {
            if (!queue.offer(event)) dropped.incrementAndGet();
        } else {
            try { queue.put(event); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        }
    }

    private void drainLoop() {
        while (running || !queue.isEmpty()) {
            try {
                LogEvent e = queue.poll(100, TimeUnit.MILLISECONDS);
                if (e != null) wrapped.append(e);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @Override
    protected void doAppend(LogEvent event, String formatted) {
        // unused; we override append() directly
    }

    public long dropped() { return dropped.get(); }

    @Override
    public synchronized void close() {
        super.close();
        running = false;
        try { drain.join(2000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        wrapped.close();
    }
}
