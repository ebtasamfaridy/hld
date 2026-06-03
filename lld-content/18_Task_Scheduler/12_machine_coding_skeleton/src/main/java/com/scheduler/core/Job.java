package com.scheduler.core;

import com.scheduler.api.Task;
import com.scheduler.trigger.Trigger;

import java.time.Instant;
import java.util.UUID;

public final class Job {
    public enum State { SCHEDULED, RUNNING, PAUSED, CANCELLED, DLQ, SUCCEEDED }

    private final String id;
    private final String name;
    private final Task task;
    private final Trigger trigger;
    private final RetryPolicy retry;
    private volatile State state;
    private volatile Instant nextFireTime;
    private volatile Instant lastFireTime;
    private volatile int attempt;

    public Job(String name, Task task, Trigger trigger, RetryPolicy retry, Instant firstFire) {
        this.id = "j-" + UUID.randomUUID();
        this.name = name; this.task = task; this.trigger = trigger; this.retry = retry;
        this.state = State.SCHEDULED;
        this.nextFireTime = firstFire;
        this.attempt = 0;
    }

    public String id()           { return id; }
    public String name()         { return name; }
    public Task task()           { return task; }
    public Trigger trigger()     { return trigger; }
    public RetryPolicy retry()   { return retry; }
    public State state()         { return state; }
    public Instant nextFire()    { return nextFireTime; }
    public Instant lastFire()    { return lastFireTime; }
    public int attempt()         { return attempt; }

    public void state(State s)              { this.state = s; }
    public void nextFire(Instant t)         { this.nextFireTime = t; }
    public void lastFire(Instant t)         { this.lastFireTime = t; }
    public void incAttempt()                { this.attempt++; }
    public void resetAttempt()              { this.attempt = 0; }
}
