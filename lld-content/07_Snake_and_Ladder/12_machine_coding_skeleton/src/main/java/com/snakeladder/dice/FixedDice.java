package com.snakeladder.dice;

import java.util.NoSuchElementException;

public final class FixedDice implements Dice {
    private final int[] values;
    private int idx = 0;
    private final int max;

    public FixedDice(int max, int... values) {
        this.max = max;
        this.values = values;
    }

    @Override
    public int roll() {
        if (idx >= values.length) throw new NoSuchElementException("no more fixed rolls");
        return values[idx++];
    }

    @Override public int max() { return max; }
}
