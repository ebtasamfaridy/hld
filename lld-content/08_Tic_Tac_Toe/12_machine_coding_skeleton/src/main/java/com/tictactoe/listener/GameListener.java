package com.tictactoe.listener;

import com.tictactoe.domain.TurnOutcome;

public interface GameListener {
    void onTurn(TurnOutcome outcome);
    default void onStart() {}
    default void onFinish(TurnOutcome terminal) {}
}
