package com.tictactoe.domain;

import java.time.Instant;

public record Move(int row, int col, Symbol symbol, Instant at) {
    public Move {
        if (row < 0 || col < 0) throw new IllegalArgumentException("non-negative");
    }
    public static Move of(int r, int c, Symbol s) { return new Move(r, c, s, Instant.now()); }
}
