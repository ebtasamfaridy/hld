package com.splitwise.domain;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class Group {
    private final UUID id;
    private final String name;
    private final Set<UUID> memberIds = new HashSet<>();
    private boolean closed = false;

    public Group(String name) { this.id = UUID.randomUUID(); this.name = name; }
    public void addMember(UUID userId) { memberIds.add(userId); }
    public void removeMember(UUID userId) { memberIds.remove(userId); }
    public boolean contains(UUID userId) { return memberIds.contains(userId); }
    public Set<UUID> members() { return Set.copyOf(memberIds); }
    public UUID id() { return id; }
    public String name() { return name; }
    public boolean closed() { return closed; }
    public void close() { this.closed = true; }
}
