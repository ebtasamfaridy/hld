package com.scheduler.api;

import com.scheduler.core.RetryPolicy;
import com.scheduler.trigger.Trigger;

public interface Scheduler extends AutoCloseable {
    JobHandle schedule(String name, Task task, Trigger trigger);
    JobHandle schedule(String name, Task task, Trigger trigger, RetryPolicy retry);

    void cancel(JobHandle handle);
    void pause(JobHandle handle);
    void resume(JobHandle handle);

    void start();
    @Override void close();
}
