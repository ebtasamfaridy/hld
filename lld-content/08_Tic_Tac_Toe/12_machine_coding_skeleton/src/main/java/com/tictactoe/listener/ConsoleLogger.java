package com.tictactoe.listener;

import com.tictactoe.domain.Board;
import com.tictactoe.domain.Symbol;
import com.tictactoe.domain.TurnOutcome;

public final class ConsoleLogger implements GameListener {

    private final Board board;

    public ConsoleLogger(Board board) { this.board = board; }

    @Override public void onStart() { System.out.println("=== game started ==="); }

    @Override
    public void onTurn(TurnOutcome o) {
        switch (o) {
            case TurnOutcome.Moved m   -> printBoard(m.player().name() + " plays " + m.move().symbol().glyph()
                                                + " at (" + m.move().row() + "," + m.move().col() + ")");
            case TurnOutcome.Won w     -> printBoard("🏆 " + w.player().name() + " wins!");
            case TurnOutcome.Drawn d   -> printBoard("🤝 draw");
            case TurnOutcome.Rejected r -> System.out.println("  rejected: " + r.reason());
        }
    }

    @Override public void onFinish(TurnOutcome t) { System.out.println("=== game over ==="); }

    private void printBoard(String header) {
        System.out.println("  " + header);
        for (int r = 0; r < board.n(); r++) {
            StringBuilder sb = new StringBuilder("  ");
            for (int c = 0; c < board.n(); c++) {
                Symbol s = board.cell(r, c);
                sb.append(s == null ? '.' : s.glyph()).append(' ');
            }
            System.out.println(sb);
        }
    }
}
