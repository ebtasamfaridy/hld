package com.snakeladder.domain;

import java.time.Instant;

public record MoveRecord(
        Instant at,
        int rollValue,
        int fromCell,
        int afterRollCell,
        int finalCell,
        Jumper jumper,
        boolean skipped,
        String reason
) {}
