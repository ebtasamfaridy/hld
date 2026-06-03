package com.snakeladder.listener;

import com.snakeladder.domain.TurnOutcome;

public interface GameEventListener {
    void onTurn(TurnOutcome outcome);
    default void onGameStarted() {}
    default void onGameFinished(TurnOutcome.Won won) {}
}
