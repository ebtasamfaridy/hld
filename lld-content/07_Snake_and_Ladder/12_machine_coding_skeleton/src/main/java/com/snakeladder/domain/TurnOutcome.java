package com.snakeladder.domain;

public sealed interface TurnOutcome permits TurnOutcome.Skipped, TurnOutcome.Moved, TurnOutcome.Won {

    Player player();

    record Skipped(Player player, int rollValue, String reason) implements TurnOutcome {}

    record Moved(Player player, int rollValue, int fromCell, int toCell, Jumper jumper) implements TurnOutcome {}

    record Won(Player player, int rollValue, int fromCell) implements TurnOutcome {}
}
