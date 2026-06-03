package com.parking.domain;

public record SpotId(String value) {
    public static SpotId of(int floor, int row, int col) {
        return new SpotId("F" + floor + "-R" + row + "-C" + col);
    }
    @Override public String toString() { return value; }
}
