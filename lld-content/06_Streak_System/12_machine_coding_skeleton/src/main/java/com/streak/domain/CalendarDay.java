package com.streak.domain;

import java.time.LocalDate;

public record CalendarDay(LocalDate day, boolean active, int eventCount) {
    public static CalendarDay inactive(LocalDate d) {
        return new CalendarDay(d, false, 0);
    }
    public static CalendarDay active(LocalDate d, int count) {
        return new CalendarDay(d, true, count);
    }
}
