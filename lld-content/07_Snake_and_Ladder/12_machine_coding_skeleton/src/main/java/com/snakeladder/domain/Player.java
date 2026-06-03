package com.snakeladder.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Player {
    private final String id;
    private final String name;
    private int position;
    private boolean started;
    private final List<MoveRecord> history = new ArrayList<>();

    public Player(String id, String name) {
        this.id = id;
        this.name = name;
        this.position = 0;
        this.started = false;
    }

    public String id()      { return id; }
    public String name()    { return name; }
    public int position()   { return position; }
    public boolean started() { return started; }
    public List<MoveRecord> history() { return Collections.unmodifiableList(history); }

    // Package-private mutators — only Game and rules reach them.
    void moveTo(int newPosition) { this.position = newPosition; }
    void start()                 { this.started = true; }
    void recordMove(MoveRecord r) { history.add(r); }
}
