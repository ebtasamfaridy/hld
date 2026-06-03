package com.snakeladder.listener;

import com.snakeladder.domain.JumperKind;
import com.snakeladder.domain.TurnOutcome;

public final class ConsoleLogger implements GameEventListener {

    @Override
    public void onTurn(TurnOutcome o) {
        switch (o) {
            case TurnOutcome.Skipped s ->
                System.out.printf("  %-8s rolled %d  ⏸  skipped (%s)%n",
                        s.player().name(), s.rollValue(), s.reason());
            case TurnOutcome.Moved m -> {
                String suffix = "";
                if (m.jumper() != null) {
                    suffix = (m.jumper().kind() == JumperKind.SNAKE)
                            ? "  🐍 SNAKE: " + m.jumper().from() + "→" + m.jumper().to()
                            : "  🪜 LADDER: " + m.jumper().from() + "→" + m.jumper().to();
                }
                System.out.printf("  %-8s rolled %d  %d → %d%s%n",
                        m.player().name(), m.rollValue(), m.fromCell(), m.toCell(), suffix);
            }
            case TurnOutcome.Won w ->
                System.out.printf("  %-8s rolled %d  🏆 WINS from cell %d!%n",
                        w.player().name(), w.rollValue(), w.fromCell());
        }
    }

    @Override public void onGameStarted() { System.out.println("=== game started ==="); }
    @Override public void onGameFinished(TurnOutcome.Won w) {
        System.out.println("=== game over: " + w.player().name() + " wins ===");
    }
}
