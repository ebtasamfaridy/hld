package com.snakeladder.rule;

import com.snakeladder.domain.Board;
import com.snakeladder.domain.Player;

import java.util.Optional;

public final class MustRollMaxToStartRule implements GameRule {
    private final int maxRoll;

    public MustRollMaxToStartRule(int maxRoll) { this.maxRoll = maxRoll; }

    @Override
    public Optional<Integer> apply(Player p, int rollValue, int proposedCell, Board board) {
        if (p.started()) return Optional.of(proposedCell);
        if (rollValue == maxRoll) return Optional.of(proposedCell);   // first move from 0 to maxRoll
        return Optional.empty();
    }

    @Override public String denyReason() { return "MUST_ROLL_" + maxRoll + "_TO_START"; }
}
