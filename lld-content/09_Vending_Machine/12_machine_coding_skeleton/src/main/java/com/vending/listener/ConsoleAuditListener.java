package com.vending.listener;

import java.time.Instant;

public final class ConsoleAuditListener implements AuditListener {
    @Override public void onEvent(Instant ts, String kind, String payload) {
        System.out.printf("  [audit] %-22s %s%n", kind, payload);
    }
}
