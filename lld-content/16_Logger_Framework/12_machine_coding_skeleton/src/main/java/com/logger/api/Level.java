package com.logger.api;

public enum Level {
    TRACE(100), DEBUG(200), INFO(300), WARN(400), ERROR(500), FATAL(600), OFF(Integer.MAX_VALUE);

    private final int rank;
    Level(int rank) { this.rank = rank; }
    public int rank() { return rank; }
    public boolean ge(Level other) { return this.rank >= other.rank; }
}
