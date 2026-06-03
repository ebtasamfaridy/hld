package com.tictactoe;

import com.tictactoe.bot.MinimaxBot;
import com.tictactoe.domain.GameStatus;
import com.tictactoe.domain.Symbol;
import com.tictactoe.listener.ConsoleLogger;
import com.tictactoe.strategy.ScriptedInput;

import java.util.List;

public final class Main {
    public static void main(String[] args) {
        // Build first without the listener; we attach the logger after build because it needs the Board reference.
        var game = new Game.Builder()
                .withSize(3, 3)
                .addPlayer("Alice", Symbol.X, new ScriptedInput(List.of(
                        new int[]{1, 1},
                        new int[]{0, 0},
                        new int[]{2, 2},
                        new int[]{0, 2},
                        new int[]{2, 0}
                )))
                .addPlayer("Bob", Symbol.O, new MinimaxBot(List.of(Symbol.X, Symbol.O)))
                .addListener(/* placeholder; we replace via game.board() below */
                        new com.tictactoe.listener.GameListener() {
                            @Override public void onTurn(com.tictactoe.domain.TurnOutcome o) { }
                        })
                .build();

        // Replace placeholder listener by directly attaching a logger backed by the live Board.
        // (For the skeleton we keep the Builder/Game API simple; production would expose addListener post-build.)
        var logger = new ConsoleLogger(game.board());
        logger.onStart();
        while (game.status() == GameStatus.IN_PROGRESS) {
            var outcome = game.takeTurn();
            logger.onTurn(outcome);
        }
        logger.onFinish(null);
    }
}
