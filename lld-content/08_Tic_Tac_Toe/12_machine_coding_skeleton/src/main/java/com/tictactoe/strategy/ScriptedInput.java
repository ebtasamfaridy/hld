package com.tictactoe.strategy;

import com.tictactoe.domain.BoardSnapshot;
import com.tictactoe.domain.Move;
import com.tictactoe.domain.Symbol;

import java.util.Iterator;
import java.util.List;

/** Plays a pre-recorded sequence of (row, col) tuples. Useful for demos and tests. */
public final class ScriptedInput implements PlayerInput {
    private final Iterator<int[]> moves;

    public ScriptedInput(List<int[]> moves) {
        this.moves = moves.iterator();
    }

    @Override
    public Move nextMove(BoardSnapshot snapshot, Symbol mySymbol) {
        int[] rc = moves.next();
        return Move.of(rc[0], rc[1], mySymbol);
    }
}
