package com.splitwise.domain;

import java.util.UUID;

public final class User {
    private final UUID id;
    private final String name;
    public User(String name) { this.id = UUID.randomUUID(); this.name = name; }
    public UUID id() { return id; }
    public String name() { return name; }
    @Override public String toString() { return name; }
}
