package com.snakeladder;

import com.snakeladder.dice.FixedDice;
import com.snakeladder.domain.Board;
import com.snakeladder.domain.GameStatus;
import com.snakeladder.listener.ConsoleLogger;
import com.snakeladder.rule.RulePacks;

public final class Main {
    public static void main(String[] args) {
        // Deterministic dice — predictable demo output.
        var dice = new FixedDice(6,
                6, 4, 6, 5, 1, 6, 2, 3, 4, 6, 5, 6, 3, 6, 1, 2,
                4, 5, 6, 6, 2, 3, 5, 4, 6, 6, 4, 1, 6, 5);

        var game = new Game.Builder()
                .withBoard(Board.standard100()
                        .withSnake(99, 7)
                        .withSnake(74, 53)
                        .withSnake(51, 19)
                        .withSnake(40, 3)
                        .withLadder(4, 25)
                        .withLadder(13, 46)
                        .withLadder(50, 69)
                        .withLadder(60, 80))
                .withDice(dice)
                .withRules(RulePacks.traditionalIndia(6))
                .addPlayer("Alice")
                .addPlayer("Bob")
                .addListener(new ConsoleLogger())
                .build();

        game.start();
        while (game.status() == GameStatus.IN_PROGRESS) {
            game.takeTurn();
        }
    }
}
