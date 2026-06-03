package com.library.policy;

public interface BorrowPolicy {
    int maxActiveLoans();
    int loanPeriodDays();
    int reservationHoldHours();
}
