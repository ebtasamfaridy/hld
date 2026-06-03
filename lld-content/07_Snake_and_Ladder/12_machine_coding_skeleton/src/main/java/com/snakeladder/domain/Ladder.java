package com.snakeladder.domain;

public record Ladder(int bottom, int top) implements Jumper {
    public Ladder {
        if (bottom >= top) throw new IllegalArgumentException("ladder bottom must be < top");
        if (bottom < 1)    throw new IllegalArgumentException("bottom must be >= 1");
    }
    @Override public int from() { return bottom; }
    @Override public int to()   { return top; }
    @Override public JumperKind kind() { return JumperKind.LADDER; }
}
