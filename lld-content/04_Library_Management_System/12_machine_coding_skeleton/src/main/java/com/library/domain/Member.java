package com.library.domain;

import java.util.UUID;

public final class Member {
    private final UUID id;
    private final String name;
    private boolean active = true;
    private int activeLoanCount = 0;
    private Money outstandingFines = Money.zero("INR");
    private long version;

    public Member(String name) {
        this.id = UUID.randomUUID();
        this.name = name;
    }
    public UUID id() { return id; }
    public String name() { return name; }
    public int activeLoanCount() { return activeLoanCount; }
    public Money outstandingFines() { return outstandingFines; }
    public boolean active() { return active; }
    public long version() { return version; }

    public void incrementLoans() { activeLoanCount++; version++; }
    public void decrementLoans() {
        if (activeLoanCount > 0) activeLoanCount--;
        version++;
    }
    public void addFine(Money m) { outstandingFines = outstandingFines.add(m); version++; }
    public void payFine(Money m) { outstandingFines = outstandingFines.subtract(m); version++; }
    public void suspend() { active = false; version++; }
    public void reinstate() { active = true; version++; }

    @Override public String toString() {
        return "Member{" + name + " loans=" + activeLoanCount
             + " fines=" + outstandingFines + (active ? "" : " SUSPENDED") + "}";
    }
}
