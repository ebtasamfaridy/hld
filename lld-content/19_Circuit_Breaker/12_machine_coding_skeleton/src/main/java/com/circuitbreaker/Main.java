package com.circuitbreaker;

import com.circuitbreaker.api.CallNotPermittedException;
import com.circuitbreaker.api.CircuitBreaker;
import com.circuitbreaker.api.CircuitBreakerConfig;
import com.circuitbreaker.api.EventListener;
import com.circuitbreaker.api.State;
import com.circuitbreaker.policy.Bulkhead;
import com.circuitbreaker.registry.CircuitBreakerRegistry;

import java.time.Duration;

public final class Main {
    public static void main(String[] args) throws Exception {
        CircuitBreakerRegistry reg = new CircuitBreakerRegistry();

        CircuitBreaker cb = reg.breaker(CircuitBreakerConfig.named("user-service")
                .windowSize(20)
                .minimumCalls(10)
                .failureRateThreshold(0.5)
                .waitDurationInOpen(Duration.ofMillis(500))
                .permittedCallsInHalfOpen(3)
                .slowCallThreshold(Duration.ofMillis(500))
                .build());

        cb.addListener(new EventListener() {
            @Override public void onStateChange(String n, State f, State t, String r) {
                System.out.printf("  [%s] %s → %s (%s)%n", n, f, t, r);
            }
            @Override public void onCallRejected(String n) {
                System.out.printf("  [%s] REJECTED%n", n);
            }
        });

        section("Phase 1: 6 successes + 6 failures → trip");
        for (int i = 0; i < 12; i++) {
            try {
                cb.execute(() -> {
                    if (Math.random() < 0.5) throw new RuntimeException("downstream 500");
                    return "ok";
                });
            } catch (Exception ignored) {}
        }
        System.out.println("  state: " + cb.state());

        section("Phase 2: while OPEN, calls reject fast");
        for (int i = 0; i < 5; i++) {
            try { cb.execute(() -> "would call"); }
            catch (CallNotPermittedException e) { /* event already logged */ }
        }

        section("Phase 3: wait, then HALF_OPEN with successful probes → CLOSED");
        Thread.sleep(550);
        for (int i = 0; i < 3; i++) {
            try { cb.execute(() -> "ok"); }
            catch (CallNotPermittedException e) { System.out.println("  rejected probe " + i); }
        }
        System.out.println("  state: " + cb.state());

        section("Phase 4: trip again, then a probe fails → OPEN");
        for (int i = 0; i < 12; i++) {
            try { cb.execute(() -> { throw new RuntimeException("still bad"); }); }
            catch (Exception ignored) {}
        }
        System.out.println("  state: " + cb.state());
        Thread.sleep(550);
        try { cb.execute(() -> { throw new RuntimeException("probe fail"); }); }
        catch (Exception ignored) {}
        System.out.println("  state after failed probe: " + cb.state());

        section("Bulkhead: limit 3 concurrent calls");
        Bulkhead bh = new Bulkhead("limiter", 3, 0);
        Thread t1 = bulkheadTask(bh, "T1", 200);
        Thread t2 = bulkheadTask(bh, "T2", 200);
        Thread t3 = bulkheadTask(bh, "T3", 200);
        t1.start(); t2.start(); t3.start();
        Thread.sleep(50);
        try { bh.execute(() -> "T4"); }
        catch (Bulkhead.BulkheadFullException e) { System.out.println("  T4 rejected: " + e.getMessage()); }
        t1.join(); t2.join(); t3.join();
    }

    private static Thread bulkheadTask(Bulkhead bh, String name, long sleepMs) {
        return new Thread(() -> bh.execute(() -> {
            try { Thread.sleep(sleepMs); } catch (InterruptedException ignored) {}
            System.out.println("  " + name + " done");
            return null;
        }));
    }

    private static void section(String s) { System.out.println("\n=== " + s + " ==="); }
}
