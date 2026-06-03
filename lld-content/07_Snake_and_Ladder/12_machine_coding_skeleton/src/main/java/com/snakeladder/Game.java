package com.snakeladder;

import com.snakeladder.dice.Dice;
import com.snakeladder.domain.*;
import com.snakeladder.listener.GameEventListener;
import com.snakeladder.rule.RuleEngine;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

public final class Game {

    private final Board board;
    private final Dice dice;
    private final RuleEngine rules;
    private final List<Player> players;
    private final List<GameEventListener> listeners;
    private final Clock clock;

    private GameStatus status = GameStatus.WAITING;
    private int currentIdx = 0;
    private Player winner;

    private Game(Board board, Dice dice, RuleEngine rules,
                 List<Player> players, List<GameEventListener> listeners, Clock clock) {
        this.board = board;
        this.dice = dice;
        this.rules = rules;
        this.players = players;
        this.listeners = listeners;
        this.clock = clock;
    }

    public void start() {
        if (status != GameStatus.WAITING) throw new IllegalStateException("already started");
        status = GameStatus.IN_PROGRESS;
        listeners.forEach(GameEventListener::onGameStarted);
    }

    public TurnOutcome takeTurn() {
        if (status != GameStatus.IN_PROGRESS) throw new IllegalStateException("not in progress");

        Player p = players.get(currentIdx);
        int roll = dice.roll();
        int from = p.position();
        int proposed = from + roll;

        var ruleOutcome = rules.finalPosition(p, roll, proposed, board);
        TurnOutcome turn;

        if (!ruleOutcome.allowed()) {
            turn = new TurnOutcome.Skipped(p, roll, ruleOutcome.denyReason());
            p.recordMove(new MoveRecord(now(), roll, from, proposed, from, null, true, ruleOutcome.denyReason()));
        } else {
            int afterRule = ruleOutcome.finalCell();
            Optional<Jumper> jumper = board.jumperAt(afterRule);
            int finalCell = jumper.map(Jumper::to).orElse(afterRule);

            // first move from 0 toggles started
            if (!p.started() && finalCell > 0) p.start();
            p.moveTo(finalCell);
            p.recordMove(new MoveRecord(now(), roll, from, afterRule, finalCell, jumper.orElse(null), false, null));

            if (finalCell == board.size()) {
                status = GameStatus.FINISHED;
                winner = p;
                turn = new TurnOutcome.Won(p, roll, from);
            } else {
                turn = new TurnOutcome.Moved(p, roll, from, finalCell, jumper.orElse(null));
            }
        }
        for (GameEventListener l : listeners) l.onTurn(turn);
        if (turn instanceof TurnOutcome.Won w) {
            for (GameEventListener l : listeners) l.onGameFinished(w);
        }

        currentIdx = (currentIdx + 1) % players.size();
        return turn;
    }

    private Instant now() { return clock.instant(); }

    public GameStatus status()           { return status; }
    public Player currentPlayer()        { return players.get(currentIdx); }
    public Optional<Player> winner()     { return Optional.ofNullable(winner); }
    public List<Player> players()        { return Collections.unmodifiableList(players); }

    // ---- Builder ----------------------------------------------------------

    public static final class Builder {
        private Board board;
        private Dice dice;
        private RuleEngine rules;
        private final List<Player> players = new ArrayList<>();
        private final List<GameEventListener> listeners = new ArrayList<>();
        private Clock clock = Clock.systemUTC();

        public Builder withBoard(Board.Builder b)    { this.board = b.build(); return this; }
        public Builder withBoard(Board b)            { this.board = b; return this; }
        public Builder withDice(Dice d)              { this.dice = d; return this; }
        public Builder withRules(RuleEngine r)       { this.rules = r; return this; }
        public Builder withClock(Clock c)            { this.clock = c; return this; }
        public Builder addPlayer(String name)        {
            players.add(new Player("p-" + (players.size() + 1), name)); return this;
        }
        public Builder addListener(GameEventListener l) { listeners.add(l); return this; }

        public Game build() {
            Objects.requireNonNull(board, "board");
            Objects.requireNonNull(dice, "dice");
            Objects.requireNonNull(rules, "rules");
            if (players.size() < 2) throw new IllegalStateException("need >= 2 players");
            return new Game(board, dice, rules, players, listeners, clock);
        }
    }
}
