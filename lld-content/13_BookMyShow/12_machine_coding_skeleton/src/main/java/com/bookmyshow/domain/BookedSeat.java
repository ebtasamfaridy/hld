package com.bookmyshow.domain;

public record BookedSeat(SeatId seatId, SeatCategory category, Money price) {}
