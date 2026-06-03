# 12 · Machine Coding Skeleton — Library

A focused Java skeleton.

## Layout

```
src/main/java/com/library
├── Main.java
├── domain/        ← Book, BookCopy, Loan, Reservation, Member, Fine
├── repository/    ← In-memory repos with CAS for copy status
├── service/       ← LoanService, ReservationService, FineService
├── policy/        ← BorrowPolicy strategies
└── api/           ← optional CLI
```

## Demo flow

1. Seed library: 1 book, 2 copies, 2 members.
2. Member A borrows copy.
3. Member B tries to borrow same book → reserved (queue position 1).
4. A returns the copy → B's reservation is promoted, copy held.
5. B borrows the held copy.
6. Late return scenario triggers fine accrual.
7. Member pays fine.

## Highlights

- `BookCopyRepository.trySetStatusCAS()` simulates SQL CAS.
- `LoanService` orchestrates atomic borrow.
- `ReservationService` manages the queue.
- `FineCalculator` strategy chain.
