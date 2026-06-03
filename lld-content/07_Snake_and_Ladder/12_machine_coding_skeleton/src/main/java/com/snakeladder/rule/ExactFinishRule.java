package com.snakeladder.rule;

import com.snakeladder.domain.Board;
import com.snakeladder.domain.Player;

import java.util.Optional;

/** Variant: finishing requires exact roll. Equivalent to StayOnOvershoot but a different "spirit." */
public final class ExactFinishRule implements GameRule {
    @Override
    public Optional<Integer> apply(Player p, int rollValue, int proposedCell, Board board) {
        if (proposedCell > board.size()) {
            return Optional.of(p.position());   // forfeit the move
        }
        return Optional.of(proposedCell);
    }
}
