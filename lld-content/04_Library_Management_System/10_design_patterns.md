# 10 · Library — Design Patterns

## 1. Strategy — fine calculators

```java
public interface FineCalculator {
  Money compute(Loan loan, LocalDate today, Branch branch);
}

class LateFeeCalculator implements FineCalculator {
  private final Money perDay;
  public Money compute(Loan loan, LocalDate today, Branch branch) {
    long lateDays = countOpenDaysBetween(loan.dueDate(), today, branch.closedDates());
    return perDay.multiply(lateDays);
  }
}

class LostBookCalculator implements FineCalculator {
  public Money compute(Loan loan, LocalDate today, Branch branch) {
    Money replacement = bookCostFor(loan.bookId());
    return replacement.multiply(1.5);
  }
}

class DamagedBookCalculator implements FineCalculator {
  public Money compute(Loan loan, LocalDate today, Branch branch) {
    Money replacement = bookCostFor(loan.bookId());
    return replacement.multiply(0.5);
  }
}

class CompositeFineCalculator implements FineCalculator {
  // delegates to the right sub-calculator based on loan.status
}
```

Adding a new fine type (e.g., HEAVY_USE_FEE) = new class. No edits.

---

## 2. Strategy — borrow policy per member type

```java
public interface BorrowPolicy {
  int maxActiveLoans();
  int loanPeriodDays();
  int renewalsAllowed();
  int renewalDays();
  int reservationHoldHours();
  Money fineCapPerLoan();
}

class StudentPolicy implements BorrowPolicy { ... }      // 5 books, 14 days
class FacultyPolicy implements BorrowPolicy { ... }      // 20 books, 30 days
class PublicMemberPolicy implements BorrowPolicy { ... } // 3 books, 7 days
```

A member is associated with a policy. LoanService uses `policy.maxActiveLoans()` and `policy.loanPeriodDays()`.

---

## 3. Strategy — copy allocation

When borrowing, which copy do we pick if many are available?

```java
public interface CopyAllocationStrategy {
  Optional<BookCopy> chooseCopy(UUID bookId, UUID preferredBranchId, List<BookCopy> available);
}

class PreferredBranchFirst implements CopyAllocationStrategy { ... }
class LowestUsageFirst implements CopyAllocationStrategy { ... }   // wear leveling
class AnyAvailableRoundRobin implements CopyAllocationStrategy { ... }
```

Most libraries use `PreferredBranchFirst` (better UX). Other strategies for niche needs.

---

## 4. State pattern — Loan

Loan transitions are state-dependent (you can't return what's already returned). For library, an enum + transition map suffices because behaviors don't diverge much per state. We document the choice; if behaviors expand, switch to State pattern classes.

---

## 5. Observer / Pub-Sub — events

```
LoanIssued        → Reservation queue check (if reserved by this member, fulfill instead of queue)
LoanReturned      → ReservationService.promoteOnAvailable
LoanOverdue       → MemberService (suspend if too many)
ReservationReady  → NotificationService
FinePaid          → MemberService (reinstate if was suspended)
```

Async via Outbox + Kafka.

---

## 6. Repository

Standard.

```java
public interface BookCopyRepository {
  Optional<BookCopy> findById(UUID);
  List<BookCopy> findAvailableForBook(UUID bookId, UUID preferredBranchId);
  boolean trySetStatusCAS(UUID copyId, CopyStatus from, CopyStatus to, long expectedVersion);
  BookCopy save(BookCopy);
}
```

The `trySetStatusCAS` reflects the SQL `UPDATE WHERE status=? AND version=?` directly.

---

## 7. Command — every mutation

```java
public record BorrowCommand(UUID memberId, UUID bookId, UUID branchId, String idempotencyKey) {}
public record ReturnCommand(UUID loanId, UUID branchId) {}
public record RenewCommand(UUID loanId) {}
public record ReserveCommand(UUID memberId, UUID bookId, UUID branchId) {}
public record CancelReservationCommand(UUID reservationId) {}
public record PayFineCommand(UUID fineId, UUID paymentMethodId, String idempotencyKey) {}
```

---

## 8. Chain of Responsibility — borrow validation

```java
List<BorrowValidator> chain = List.of(
  new AuthValidator(),
  new MemberActiveValidator(),
  new NoOutstandingFinesValidator(),
  new UnderLoanLimitValidator(),
  new BookExistsValidator(),
  new RateLimitValidator()
);

for (var v : chain) v.validate(cmd, ctx);
```

Each validator throws a typed error if it fails.

---

## 9. Decorator — cross-cutting

LogService → MetricsService → TracingService → CoreLoanService.

---

## 10. Factory — policy selection

```java
public class BorrowPolicyFactory {
  public BorrowPolicy forMember(Member m) {
    return switch (m.tier()) {
      case STUDENT -> new StudentPolicy();
      case FACULTY -> new FacultyPolicy();
      case PUBLIC  -> new PublicMemberPolicy();
    };
  }
}
```

---

## 11. Adapter — payment / notification

`PaymentGateway` and `NotificationProvider` are interfaces; Stripe / Twilio / SES live behind adapters.

---

## 12. Reservation queue — Producer/Consumer

The reservation queue head selection is an implicit producer/consumer:

- Producers: events that may free up a copy (`LoanReturned`, `CopyAvailable`, `ReservationExpired`).
- Consumer: a single-writer worker per book that promotes the head.

Backed by `SELECT FOR UPDATE SKIP LOCKED` to allow safe parallelism across different books.

---

## SOLID

- **S**: each calculator, validator, policy = one job.
- **O**: new fine kind / policy / allocation = new class.
- **L**: every Strategy honors its interface.
- **I**: small interfaces (BookCopyRepository ~6 methods).
- **D**: services depend on interfaces.

---

## What we deliberately avoided

- **Inheritance hierarchy of Loan subclasses** (StudentLoan, FacultyLoan) — over-engineered. A single Loan with policy attached is cleaner.
- **Storing fines inside the Loan row** — separate aggregate; clearer lifecycle.
- **Coupling reservation queue to the Book table** — separate Reservation aggregate; queries are clean.
