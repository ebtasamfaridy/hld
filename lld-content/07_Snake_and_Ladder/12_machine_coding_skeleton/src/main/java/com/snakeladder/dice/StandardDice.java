package com.snakeladder.dice;

import java.util.Random;

public final class StandardDice implements Dice {
    private final int sides;
    private final Random rng;

    public StandardDice(int sides, Random rng) {
        if (sides < 2) throw new IllegalArgumentException("sides>=2");
        this.sides = sides;
        this.rng = rng;
    }

    @Override public int roll() { return rng.nextInt(sides) + 1; }
    @Override public int max()  { return sides; }
}
