package com.streak.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

public record RawAppEvent(
        String eventId,
        UserId userId,
        Kind kind,
        Map<String, Object> metadata,
        Instant occurredAt,
        ZoneId userTimezone
) {
    public enum Kind { SESSION_STARTED, EPISODE_PLAYED, OTHER }
}
