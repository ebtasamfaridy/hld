package com.tictactoe.domain;

public sealed interface TurnOutcome
        permits TurnOutcome.Moved,
                TurnOutcome.Won,
                TurnOutcome.Drawn,
                TurnOutcome.Rejected {

    record Moved(Player player, Move move) implements TurnOutcome {}
    record Won(Player player, Move move) implements TurnOutcome {}
    record Drawn(Move lastMove) implements TurnOutcome {}
    record Rejected(Player player, Move attempted, RejectReason reason) implements TurnOutcome {}

    enum RejectReason { OUT_OF_BOUNDS, OCCUPIED, NOT_YOUR_TURN, GAME_OVER }
}
