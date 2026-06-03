package com.bookmyshow.domain;

public record SeatId(String value) {
    public static SeatId of(char row, int col) { return new SeatId("" + row + col); }
    @Override public String toString() { return value; }
}
