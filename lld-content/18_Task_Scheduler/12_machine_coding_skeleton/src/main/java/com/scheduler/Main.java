package com.scheduler;

import com.scheduler.api.Scheduler;
import com.scheduler.api.Task;
import com.scheduler.core.RetryPolicy;
import com.scheduler.executor.InProcessScheduler;
import com.scheduler.trigger.CronTrigger;
import com.scheduler.trigger.FixedRateTrigger;
import com.scheduler.trigger.OneShotTrigger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public final class Main {
    public static void main(String[] args) throws Exception {
        Scheduler sch = new InProcessScheduler(Clock.systemUTC(), 4);
        sch.start();

        Task heartbeat = ctx ->
                System.out.printf("  [%-10s] tick attempt=%d scheduledFor=%s key=%s%n",
                        ctx.jobName(), ctx.attempt(), ctx.scheduledFor(), ctx.idempotencyKey());

        Task once = ctx -> System.out.printf("  [%-10s] one-shot fired at %s%n", ctx.jobName(), ctx.scheduledFor());

        AtomicInteger flakyCount = new AtomicInteger();
        Task flaky = ctx -> {
            int n = flakyCount.incrementAndGet();
            System.out.printf("  [%-10s] attempt=%d (n=%d)%n", ctx.jobName(), ctx.attempt(), n);
            if (n < 3) throw new RuntimeException("simulated transient failure");
            System.out.println("  [flaky    ] success on attempt " + ctx.attempt());
        };

        sch.schedule("heartbeat", heartbeat,
                new FixedRateTrigger(Instant.now().plusMillis(100), Duration.ofMillis(300)));

        sch.schedule("once",      once,
                new OneShotTrigger(Instant.now().plusMillis(500)));

        sch.schedule("cron",      heartbeat,
                new CronTrigger("*/1 s"));

        sch.schedule("flaky",     flaky,
                new OneShotTrigger(Instant.now().plusMillis(200)),
                RetryPolicy.of(5, Duration.ofMillis(100)));

        Thread.sleep(3500);
        sch.close();
        System.out.println("\n done");
    }
}
