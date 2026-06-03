package com.scheduler.executor;

import com.scheduler.api.JobHandle;
import com.scheduler.api.Scheduler;
import com.scheduler.api.Task;
import com.scheduler.api.TaskContext;
import com.scheduler.core.Job;
import com.scheduler.core.RetryPolicy;
import com.scheduler.store.InMemoryJobStore;
import com.scheduler.store.JobStore;
import com.scheduler.trigger.Trigger;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.*;

public final class InProcessScheduler implements Scheduler {

    private final JobStore store;
    private final ExecutorService workers;
    private final ScheduledExecutorService ticker;
    private final Clock clock;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();
    private volatile boolean running = false;

    public InProcessScheduler() { this(Clock.systemUTC(), 8); }

    public InProcessScheduler(Clock clock, int poolSize) {
        this.clock = clock;
        this.store = new InMemoryJobStore();
        this.workers = Executors.newFixedThreadPool(poolSize, daemon("sch-worker"));
        this.ticker = Executors.newSingleThreadScheduledExecutor(daemon("sch-ticker"));
    }

    @Override public void start() { running = true; }

    @Override
    public JobHandle schedule(String name, Task task, Trigger trigger) {
        return schedule(name, task, trigger, RetryPolicy.none());
    }

    @Override
    public JobHandle schedule(String name, Task task, Trigger trigger, RetryPolicy retry) {
        Instant now = clock.instant();
        Instant firstFire = trigger.nextFireTime(null, now)
                .orElseThrow(() -> new IllegalArgumentException("trigger produced no fire time"));
        Job j = new Job(name, task, trigger, retry, firstFire);
        store.put(j);
        scheduleTick(j);
        return new JobHandle(j.id());
    }

    private void scheduleTick(Job j) {
        long delayMs = Math.max(0, j.nextFire().toEpochMilli() - clock.millis());
        ScheduledFuture<?> f = ticker.schedule(() -> fire(j), delayMs, TimeUnit.MILLISECONDS);
        pending.put(j.id(), f);
    }

    private void fire(Job j) {
        if (!running || j.state() == Job.State.CANCELLED || j.state() == Job.State.PAUSED) return;
        workers.submit(() -> runOnce(j));
    }

    private void runOnce(Job j) {
        Instant scheduled = j.nextFire();
        int attempt = j.attempt() + 1;
        String idem = j.id() + ":" + scheduled.toEpochMilli() + ":" + attempt;
        TaskContext ctx = new TaskContext(j.id(), j.name(), scheduled, attempt, idem);

        j.state(Job.State.RUNNING);
        try {
            j.task().execute(ctx);
            // success
            j.lastFire(scheduled);
            j.resetAttempt();
            // schedule next via trigger; if exhausted → SUCCEEDED
            Optional<Instant> next = j.trigger().nextFireTime(scheduled, clock.instant());
            if (next.isEmpty()) {
                j.state(Job.State.SUCCEEDED);
            } else {
                j.nextFire(next.get());
                j.state(Job.State.SCHEDULED);
                scheduleTick(j);
            }
        } catch (Exception e) {
            j.incAttempt();
            if (j.retry().shouldRetry(j.attempt())) {
                Instant next = clock.instant().plus(j.retry().nextDelay(j.attempt()));
                j.nextFire(next);
                j.state(Job.State.SCHEDULED);
                scheduleTick(j);
                System.err.printf("    [retry] %s attempt=%d error=%s%n", j.name(), j.attempt(), e);
            } else {
                j.state(Job.State.DLQ);
                System.err.printf("    [DLQ] %s gave up after %d attempts: %s%n", j.name(), j.attempt(), e);
            }
        }
    }

    @Override public void cancel(JobHandle h) {
        store.get(h.jobId()).ifPresent(j -> j.state(Job.State.CANCELLED));
        ScheduledFuture<?> f = pending.remove(h.jobId());
        if (f != null) f.cancel(false);
    }
    @Override public void pause(JobHandle h)  { store.get(h.jobId()).ifPresent(j -> j.state(Job.State.PAUSED)); }
    @Override public void resume(JobHandle h) { store.get(h.jobId()).ifPresent(j -> { j.state(Job.State.SCHEDULED); scheduleTick(j); }); }

    @Override
    public void close() {
        running = false;
        ticker.shutdown();
        workers.shutdown();
    }

    private static ThreadFactory daemon(String name) {
        return r -> { Thread t = new Thread(r, name); t.setDaemon(true); return t; };
    }
}
