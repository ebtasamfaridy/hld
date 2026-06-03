package com.snakeladder.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Board {
    private final int size;
    private final Map<Integer, Jumper> jumpers;

    public Board(int size, List<Jumper> jumperList) {
        if (size < 2) throw new IllegalArgumentException("board too small");
        this.size = size;
        this.jumpers = new HashMap<>();
        for (Jumper j : jumperList) {
            if (j.from() < 1 || j.from() > size)
                throw new IllegalArgumentException("jumper from-cell out of bounds: " + j.from());
            if (j.to() < 1 || j.to() > size)
                throw new IllegalArgumentException("jumper to-cell out of bounds: " + j.to());
            if (j.from() == size)
                throw new IllegalArgumentException("cannot place jumper on last cell");
            if (jumpers.containsKey(j.from()))
                throw new IllegalArgumentException("two jumpers on same from-cell: " + j.from());
            jumpers.put(j.from(), j);
        }
    }

    public Optional<Jumper> jumperAt(int cell) {
        return Optional.ofNullable(jumpers.get(cell));
    }

    public int size() { return size; }

    public static Builder standard100() { return new Builder(100); }

    public static final class Builder {
        private final int size;
        private final java.util.ArrayList<Jumper> list = new java.util.ArrayList<>();
        public Builder(int size) { this.size = size; }
        public Builder withSnake(int head, int tail)   { list.add(new Snake(head, tail));   return this; }
        public Builder withLadder(int bottom, int top) { list.add(new Ladder(bottom, top)); return this; }
        public Board build() { return new Board(size, list); }
    }
}
