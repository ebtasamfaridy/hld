# 07 · Library — Class Diagrams

## Aggregated class diagram

A single view of every class, interface, and enum in the system — fields and methods included. Source of truth: the Java skeleton under `12_machine_coding_skeleton/`. Use this when you want the whole system on one page; the categorical diagrams below break out individual concerns (Domain / Services / Strategies / Repositories / State) for focused reading.

```mermaid
classDiagram
    %% ===== Value objects & enums =====
    class Money {
      -long amountMinor
      -String currency
      +inr(rupees) Money$
      +zero(currency) Money$
      +add(other) Money
      +isPositive() boolean
      +amountMinor() long
      +currency() String
    }
    class CopyStatus {
      <<enumeration>>
      AVAILABLE
      BORROWED
      RESERVED_HOLD
      LOST
      DAMAGED
      IN_REPAIR
      IN_TRANSIT
    }
    class LoanStatus {
      <<enumeration>>
      ACTIVE
      RETURNED
      OVERDUE
      LOST
      DAMAGED
    }
    class ReservationStatus {
      <<enumeration>>
      QUEUED
      READY
      FULFILLED
      EXPIRED
      CANCELLED
    }
    class MemberStatus {
      <<enumeration>>
      ACTIVE
      SUSPENDED
    }
    class FineStatus {
      <<enumeration>>
      PENDING
      PAID
      WAIVED
    }
    class FineKind {
      <<enumeration>>
      OVERDUE
      LOST
      DAMAGED
    }

    %% ===== Domain entities =====
    class Book {
      -UUID id
      -String isbn
      -String title
      -List~String~ authors
      +id() UUID
      +isbn() String
      +title() String
      +authors() List~String~
    }
    class BookCopy {
      -UUID id
      -UUID bookId
      -UUID branchId
      -CopyStatus status
      -long version
      +id() UUID
      +bookId() UUID
      +branchId() UUID
      +status() CopyStatus
      +version() long
    }
    class Loan {
      -UUID id
      -UUID memberId
      -UUID copyId
      -UUID branchId
      -LocalDate dueDate
      -LoanStatus status
      -int renewals
      -Instant returnedAt
      -long version
      +renew(days)
      +returned()
      +markLost()
      +markDamaged()
      +memberId() UUID
      +copyId() UUID
      +status() LoanStatus
    }
    class Reservation {
      -UUID id
      -UUID memberId
      -UUID bookId
      -UUID branchId
      -ReservationStatus status
      -int queuePosition
      -Instant readyAt
      -Instant expiresAt
      +promote(copyId)
      +fulfill()
      +cancel()
      +expire()
    }
    class Member {
      -UUID id
      -String name
      -MemberStatus status
      -int activeLoanCount
      -Money outstandingFines
      +canBorrow() boolean
      +incrementLoans()
      +decrementLoans()
      +active() boolean
      +activeLoanCount() int
      +outstandingFines() Money
    }
    class Fine {
      -UUID id
      -UUID memberId
      -UUID loanId
      -FineKind kind
      -Money amount
      -FineStatus status
      +pay(amount)
      +waive(actor, reason)
    }

    %% ===== Strategies =====
    class BorrowPolicy {
      <<interface>>
      +maxActiveLoans() int
      +loanPeriodDays() int
      +renewalsAllowed() int
      +renewalDays() int
      +reservationHoldDays() int
    }
    class StandardPolicy
    BorrowPolicy <|.. StandardPolicy

    class FineCalculator {
      <<interface>>
      +compute(loan, today) Money
    }
    class LateFeeCalculator
    class LostBookCalculator
    class DamagedBookCalculator
    FineCalculator <|.. LateFeeCalculator
    FineCalculator <|.. LostBookCalculator
    FineCalculator <|.. DamagedBookCalculator

    %% ===== Repositories =====
    class BookRepository {
      <<interface>>
      +findById(id) Optional~Book~
      +save(book) Book
    }
    class BookCopyRepository {
      <<interface>>
      +findById(id) Optional~BookCopy~
      +findFirstAvailable(bookId, branchId) Optional~BookCopy~
      +trySetStatusCAS(id, expected, next, version) boolean
      +save(copy) BookCopy
    }
    class LoanRepository {
      <<interface>>
      +findById(id) Optional~Loan~
      +findActiveForMember(memberId) List~Loan~
      +findActiveForCopy(copyId) Optional~Loan~
      +findOverdueAsOf(date) List~Loan~
      +save(loan) Loan
    }
    class ReservationRepository {
      <<interface>>
      +findHeadOfQueue(bookId) Optional~Reservation~
      +findActiveByMember(memberId) List~Reservation~
      +save(reservation) Reservation
    }
    class MemberRepository {
      <<interface>>
      +findById(id) Optional~Member~
      +save(member) Member
    }
    class FineRepository {
      <<interface>>
      +findById(id) Optional~Fine~
      +findPendingForMember(memberId) List~Fine~
      +save(fine) Fine
    }

    %% ===== Application services =====
    class LoanService {
      -LoanRepository loans
      -BookCopyRepository copies
      -MemberRepository members
      -BorrowPolicy policy
      -ReservationService reservations
      +borrow(memberId, bookId, branchId) Optional~Loan~
      +returnLoan(loanId) Loan
      +renew(loanId, days) Loan
    }
    class ReservationService {
      -ReservationRepository repo
      -BookCopyRepository copies
      +reserve(memberId, bookId, branchId) Reservation
      +cancel(reservationId)
      +tryPromote(bookId, copyId, version) UUID
      +hasQueued(bookId) boolean
      +expireUnclaimed()
    }
    class FineService {
      -FineRepository fines
      -LoanRepository loans
      -List~FineCalculator~ calculators
      +accrueDailyOverdues(today)
      +finalizeFinesOnReturn(loan, returnedAt)
      +recordLost(loan)
      +recordDamaged(loan)
      +pay(fineId, paymentMethod) Fine
      +waive(fineId, actor, reason) Fine
    }

    %% ===== Relationships =====
    Book "1" o-- "*" BookCopy : has copies
    Loan ..> BookCopy : tracks
    Loan ..> Member : borrowed by
    Reservation ..> Book : queued for
    Reservation ..> Member : placed by
    Fine ..> Loan : derives from
    Fine ..> Member : owed by

    Member ..> Money
    Fine ..> Money

    LoanService ..> LoanRepository
    LoanService ..> BookCopyRepository
    LoanService ..> MemberRepository
    LoanService ..> BorrowPolicy
    LoanService ..> ReservationService
    ReservationService ..> ReservationRepository
    ReservationService ..> BookCopyRepository
    FineService ..> FineRepository
    FineService ..> LoanRepository
    FineService o-- "*" FineCalculator
```

---

## Domain

```mermaid
classDiagram
  class Book {
    -UUID id
    -String isbn
    -String title
    -List~String~ authors
    -List~String~ genres
  }

  class BookCopy {
    -UUID id
    -UUID bookId
    -UUID branchId
    -String shelfLocation
    -CopyStatus status
    -long version
    +markBorrowed()
    +markAvailable()
    +markLost()
    +markInRepair()
    +markInTransit(toBranch)
  }

  class Loan {
    -UUID id
    -UUID memberId
    -UUID copyId
    -LocalDate dueDate
    -LoanStatus status
    -int renewals
    -Instant returnedAt
    -long version
    +renew()
    +returned(branch)
    +markLost()
    +markDamaged()
  }

  class Reservation {
    -UUID id
    -UUID memberId
    -UUID bookId
    -ReservationStatus status
    -int queuePosition
    -Instant readyAt
    -Instant expiresAt
    +promote()
    +expire()
    +fulfill()
    +cancel()
  }

  class Member {
    -UUID id
    -MemberStatus status
    -int activeLoanCount
    -Money outstandingFineBalance
    +canBorrow() bool
    +incrementLoans()
    +decrementLoans()
  }

  class Fine {
    -UUID id
    -UUID memberId
    -UUID loanId
    -FineKind kind
    -Money amount
    -FineStatus status
    +pay(amount)
    +waive(actor, reason)
  }

  Book "1" o-- "*" BookCopy
  Loan ..> BookCopy
  Loan ..> Member
  Reservation ..> Book
  Reservation ..> Member
  Fine ..> Loan
```

---

## Application services

```mermaid
classDiagram
  class LoanService {
    -LoanRepository loans
    -BookCopyRepository copies
    -MemberRepository members
    -ReservationService reservations
    -EventPublisher events
    -BorrowPolicy policy
    +borrow(memberId, bookId, branchId, idemKey)
    +returnLoan(loanId, branchId)
    +renew(loanId)
  }

  class ReservationService {
    -ReservationRepository repo
    -BookCopyRepository copies
    -EventPublisher events
    +reserve(memberId, bookId, branchId)
    +cancel(reservationId)
    +promoteOnAvailable(bookId, copyId, branchId)
    +expireUnclaimed()
  }

  class FineService {
    -FineRepository fines
    -LoanRepository loans
    -List~FineCalculator~ calculators
    +accrueDailyOverdues(today)
    +finalizeFinesOnReturn(loan, returnedAt)
    +recordLost(loan)
    +recordDamaged(loan)
    +pay(fineId, paymentMethod, idemKey)
    +waive(fineId, actor, reason)
  }

  class CatalogService {
    +addBook
    +addCopies
    +transferCopy
    +markLost / markRepair
  }

  class MemberService { ... }

  LoanService --> ReservationService
  LoanService --> FineService
  ReservationService --> EventPublisher
```

---

## Strategies

```mermaid
classDiagram
  class FineCalculator {
    <<interface>>
    +compute(Loan, today, Branch) Money
  }
  class LateFeeCalculator
  class LostBookCalculator
  class DamagedBookCalculator
  class CompositeFineCalculator
  FineCalculator <|.. LateFeeCalculator
  FineCalculator <|.. LostBookCalculator
  FineCalculator <|.. DamagedBookCalculator
  FineCalculator <|.. CompositeFineCalculator

  class BorrowPolicy {
    <<interface>>
    +maxActiveLoans()
    +loanPeriodDays()
    +renewalsAllowed()
    +renewalDays()
    +reservationHoldDays()
  }
  class StudentPolicy
  class FacultyPolicy
  class PublicMemberPolicy
  BorrowPolicy <|.. StudentPolicy
  BorrowPolicy <|.. FacultyPolicy
  BorrowPolicy <|.. PublicMemberPolicy

  class CopyAllocationStrategy {
    <<interface>>
    +chooseCopy(book, preferredBranch, copies) BookCopy
  }
  class PreferredBranchFirst
  class AnyBranchAvailable
  CopyAllocationStrategy <|.. PreferredBranchFirst
  CopyAllocationStrategy <|.. AnyBranchAvailable
```

---

## Repositories

```mermaid
classDiagram
  class BookRepository { <<interface>> }
  class BookCopyRepository {
    <<interface>>
    +findAvailableForBook(bookId, preferredBranch) Optional~BookCopy~
    +saveWithCAS(copy) bool
  }
  class LoanRepository {
    <<interface>>
    +findActiveForMember(memberId)
    +findActiveForCopy(copyId)
    +findOverdueAsOf(date)
  }
  class ReservationRepository {
    <<interface>>
    +findHeadOfQueue(bookId)
    +findActiveByMember(memberId)
  }
  class MemberRepository
  class FineRepository
```

---

## State pattern (Loan)

```mermaid
classDiagram
  class LoanState {
    <<interface>>
    +renew(Loan)
    +returned(Loan, branch)
    +markLost(Loan)
    +markDamaged(Loan)
  }
  class BorrowedState
  class OverdueState
  class ReturnedState
  class LostState
  class DamagedState
  LoanState <|.. BorrowedState
  LoanState <|.. OverdueState
  LoanState <|.. ReturnedState
  LoanState <|.. LostState
  LoanState <|.. DamagedState
```

OVERDUE behaves mostly like BORROWED for return; we still split for clarity (different fine accrual).

---

## Layering

```mermaid
flowchart LR
  api --> application
  application --> domain
  application --> infra
  infra --> domain
```

Same Clean Architecture as other systems.
