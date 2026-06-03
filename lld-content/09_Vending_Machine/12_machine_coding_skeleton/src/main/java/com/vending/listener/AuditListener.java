package com.vending.listener;

import java.time.Instant;

public interface AuditListener {
    void onEvent(Instant ts, String kind, String payload);
}
