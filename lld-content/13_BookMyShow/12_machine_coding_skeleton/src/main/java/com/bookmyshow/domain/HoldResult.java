package com.bookmyshow.domain;

import java.util.List;

public sealed interface HoldResult permits HoldResult.Created, HoldResult.Conflict, HoldResult.ShowClosed {
    record Created(Hold hold) implements HoldResult {}
    record Conflict(List<SeatId> conflicting) implements HoldResult {}
    record ShowClosed(String reason) implements HoldResult {}
}
