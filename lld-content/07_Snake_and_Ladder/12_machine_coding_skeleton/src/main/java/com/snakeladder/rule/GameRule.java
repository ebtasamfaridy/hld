package com.snakeladder.rule;

import com.snakeladder.domain.Board;
import com.snakeladder.domain.Player;

import java.util.Optional;

public interface GameRule {
    /**
     * Returns the effective post-rule cell, or empty if the move is forbidden.
     * Rules are composed in order; each receives the previous rule's output.
     */
    Optional<Integer> apply(Player player, int rollValue, int proposedCell, Board board);

    /** Optional descriptor used to fill the "skipped reason" string. */
    default String denyReason() { return getClass().getSimpleName(); }
}
