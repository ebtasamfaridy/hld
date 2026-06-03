package com.tictactoe.strategy;

import com.tictactoe.domain.BoardSnapshot;
import com.tictactoe.domain.Move;
import com.tictactoe.domain.Symbol;

public interface PlayerInput {
    Move nextMove(BoardSnapshot snapshot, Symbol mySymbol);
}
