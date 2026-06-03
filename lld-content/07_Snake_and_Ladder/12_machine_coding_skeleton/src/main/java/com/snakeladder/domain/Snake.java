package com.snakeladder.domain;

public record Snake(int head, int tail) implements Jumper {
    public Snake {
        if (head <= tail) throw new IllegalArgumentException("snake head must be > tail");
        if (tail < 1)     throw new IllegalArgumentException("tail must be >= 1");
    }
    @Override public int from() { return head; }
    @Override public int to()   { return tail; }
    @Override public JumperKind kind() { return JumperKind.SNAKE; }
}
