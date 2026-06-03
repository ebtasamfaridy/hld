package com.tictactoe.domain;

import java.util.Objects;

public record Symbol(char glyph, int idx) {
    public Symbol {
        if (idx < 0) throw new IllegalArgumentException("idx must be >= 0");
        Objects.requireNonNull(glyph);
    }
    public static final Symbol X = new Symbol('X', 0);
    public static final Symbol O = new Symbol('O', 1);
}
