package com.tictactoe.bot;

import com.tictactoe.domain.*;
import com.tictactoe.strategy.PlayerInput;

import java.util.List;

/**
 * Standard minimax with alpha-beta pruning, sufficient for 3x3.
 * For larger boards, swap in MCTS or a heuristic evaluator.
 */
public final class MinimaxBot implements PlayerInput {

    private final List<Symbol> allSymbols;

    public MinimaxBot(List<Symbol> allSymbols) {
        this.allSymbols = List.copyOf(allSymbols);
    }

    @Override
    public Move nextMove(BoardSnapshot s, Symbol me) {
        Board b = boardFromSnapshot(s);
        int bestScore = Integer.MIN_VALUE;
        Move bestMove = null;
        for (int r = 0; r < b.n(); r++) {
            for (int c = 0; c < b.n(); c++) {
                if (b.cell(r, c) != null) continue;
                Move attempt = Move.of(r, c, me);
                b.place(attempt);
                int score = b.wonBy(me, r, c) ? 1000 : -minimax(b, opponent(me), me, Integer.MIN_VALUE, Integer.MAX_VALUE, b.n() * b.n());
                b.undo(r, c);
                if (score > bestScore) { bestScore = score; bestMove = attempt; }
            }
        }
        return bestMove;
    }

    private int minimax(Board b, Symbol toMove, Symbol me, int alpha, int beta, int depth) {
        if (depth == 0 || b.isFull()) return 0;
        boolean maximizing = (toMove == me);
        int best = maximizing ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (int r = 0; r < b.n(); r++) {
            for (int c = 0; c < b.n(); c++) {
                if (b.cell(r, c) != null) continue;
                b.place(Move.of(r, c, toMove));
                int score;
                if (b.wonBy(toMove, r, c)) {
                    score = (toMove == me) ? +(100 + depth) : -(100 + depth);
                } else if (b.isFull()) {
                    score = 0;
                } else {
                    score = minimax(b, opponent(toMove), me, alpha, beta, depth - 1);
                }
                b.undo(r, c);
                if (maximizing) {
                    best = Math.max(best, score);
                    alpha = Math.max(alpha, best);
                } else {
                    best = Math.min(best, score);
                    beta = Math.min(beta, best);
                }
                if (beta <= alpha) return best;
            }
        }
        return best;
    }

    private Symbol opponent(Symbol s) {
        for (Symbol other : allSymbols) if (other != s) return other;
        return s;   // single-symbol degenerate
    }

    private Board boardFromSnapshot(BoardSnapshot s) {
        Board b = new Board(s.n(), s.k(), allSymbols.size());
        for (int r = 0; r < s.n(); r++)
            for (int c = 0; c < s.n(); c++)
                if (s.cells()[r][c] != null) b.place(Move.of(r, c, s.cells()[r][c]));
        return b;
    }
}
