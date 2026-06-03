package com.snakeladder.rule;

import com.snakeladder.domain.Board;
import com.snakeladder.domain.Player;

import java.util.Optional;

public final class StayOnOvershootRule implements GameRule {
    @Override
    public Optional<Integer> apply(Player p, int rollValue, int proposedCell, Board board) {
        if (proposedCell > board.size()) {
            return Optional.of(p.position());   // stay put
        }
        return Optional.of(proposedCell);
    }
}
