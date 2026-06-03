package com.library.policy;

public final class StandardPolicy implements BorrowPolicy {
    @Override public int maxActiveLoans()       { return 5; }
    @Override public int loanPeriodDays()       { return 14; }
    @Override public int reservationHoldHours() { return 24; }
}
