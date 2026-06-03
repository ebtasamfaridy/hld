package com.tictactoe.domain;

import com.tictactoe.strategy.PlayerInput;

public final class Player {
    private final String id;
    private final String name;
    private final Symbol symbol;
    private PlayerInput input;     // mutable to support AFK auto-bot

    public Player(String id, String name, Symbol symbol, PlayerInput input) {
        this.id = id; this.name = name; this.symbol = symbol; this.input = input;
    }
    public String id()         { return id; }
    public String name()       { return name; }
    public Symbol symbol()     { return symbol; }
    public PlayerInput input() { return input; }
    public void setInput(PlayerInput i) { this.input = i; }
}
