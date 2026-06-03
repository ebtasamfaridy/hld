package com.tictactoe;

import com.tictactoe.domain.*;
import com.tictactoe.listener.GameListener;
import com.tictactoe.strategy.PlayerInput;

import java.util.*;

public final class Game {

    private final Board board;
    private final List<Player> players;
    private final List<GameListener> listeners;

    private GameStatus status = GameStatus.WAITING;
    private int currentIdx = 0;
    private Player winner;

    private Game(Board board, List<Player> players, List<GameListener> listeners) {
        this.board = board; this.players = players; this.listeners = listeners;
    }

    public void start() {
        if (status != GameStatus.WAITING) throw new IllegalStateException("already started");
        status = GameStatus.IN_PROGRESS;
        listeners.forEach(GameListener::onStart);
    }

    public TurnOutcome takeTurn() {
        if (status != GameStatus.IN_PROGRESS) throw new IllegalStateException("not in progress");
        Player p = players.get(currentIdx);
        Move m = p.input().nextMove(board.snapshot(), p.symbol());

        var rejection = validate(p, m);
        if (rejection != null) {
            TurnOutcome rej = new TurnOutcome.Rejected(p, m, rejection);
            listeners.forEach(l -> l.onTurn(rej));
            return rej;
        }

        boolean placed = board.place(m);
        if (!placed) {
            TurnOutcome rej = new TurnOutcome.Rejected(p, m, TurnOutcome.RejectReason.OCCUPIED);
            listeners.forEach(l -> l.onTurn(rej));
            return rej;
        }

        if (board.wonBy(p.symbol(), m.row(), m.col())) {
            status = GameStatus.WON;
            winner = p;
            TurnOutcome won = new TurnOutcome.Won(p, m);
            listeners.forEach(l -> l.onTurn(won));
            listeners.forEach(l -> l.onFinish(won));
            return won;
        }
        if (board.isFull()) {
            status = GameStatus.DRAWN;
            TurnOutcome drawn = new TurnOutcome.Drawn(m);
            listeners.forEach(l -> l.onTurn(drawn));
            listeners.forEach(l -> l.onFinish(drawn));
            return drawn;
        }

        TurnOutcome moved = new TurnOutcome.Moved(p, m);
        listeners.forEach(l -> l.onTurn(moved));
        currentIdx = (currentIdx + 1) % players.size();
        return moved;
    }

    private TurnOutcome.RejectReason validate(Player p, Move m) {
        int n = board.n();
        if (m.row() < 0 || m.row() >= n || m.col() < 0 || m.col() >= n)
            return TurnOutcome.RejectReason.OUT_OF_BOUNDS;
        if (m.symbol() != p.symbol())
            return TurnOutcome.RejectReason.NOT_YOUR_TURN;
        return null;
    }

    public GameStatus status()           { return status; }
    public Optional<Player> winner()     { return Optional.ofNullable(winner); }
    public Player currentPlayer()        { return players.get(currentIdx); }
    public Board board()                 { return board; }

    public static final class Builder {
        private int n = 3, k = 3;
        private final List<Player> players = new ArrayList<>();
        private final List<GameListener> listeners = new ArrayList<>();

        public Builder withSize(int n, int k) { this.n = n; this.k = k; return this; }
        public Builder addPlayer(String name, Symbol symbol, PlayerInput input) {
            players.add(new Player("p-" + (players.size() + 1), name, symbol, input));
            return this;
        }
        public Builder addListener(GameListener l) { listeners.add(l); return this; }

        public Game build() {
            if (players.size() < 2) throw new IllegalStateException(">= 2 players");
            int symbolCount = players.size();
            // Validate all symbols are distinct
            Set<Symbol> seen = new HashSet<>();
            for (Player p : players) if (!seen.add(p.symbol())) throw new IllegalStateException("dup symbol");
            Board board = new Board(n, k, symbolCount);
            return new Game(board, players, listeners);
        }
    }
}
