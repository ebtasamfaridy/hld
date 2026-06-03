package com.snakeladder.rule;

import com.snakeladder.domain.Board;
import com.snakeladder.domain.Player;

import java.util.List;
import java.util.Optional;

public final class RuleEngine {
    private final List<GameRule> rules;

    public RuleEngine(List<GameRule> rules) {
        this.rules = List.copyOf(rules);
    }

    /** Returns final cell after composing rules; or empty if any rule denied the move. */
    public Outcome finalPosition(Player player, int rollValue, int proposed, Board board) {
        int current = proposed;
        for (GameRule r : rules) {
            Optional<Integer> next = r.apply(player, rollValue, current, board);
            if (next.isEmpty()) return Outcome.denied(r.denyReason());
            current = next.get();
        }
        return Outcome.allowed(current);
    }

    public record Outcome(boolean allowed, int finalCell, String denyReason) {
        public static Outcome allowed(int cell)        { return new Outcome(true,  cell, null); }
        public static Outcome denied(String reason)    { return new Outcome(false, -1,   reason); }
    }
}
