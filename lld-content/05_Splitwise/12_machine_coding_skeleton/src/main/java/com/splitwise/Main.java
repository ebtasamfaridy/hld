package com.splitwise;

import com.splitwise.domain.*;
import com.splitwise.service.BalanceService;
import com.splitwise.service.ExpenseService;
import com.splitwise.simplify.Transfer;
import com.splitwise.split.SplitStrategyFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Main {
    public static void main(String[] args) {
        var balances = new BalanceService();
        var expenseSvc = new ExpenseService(balances, new SplitStrategyFactory());

        User alice = new User("Alice");
        User bob   = new User("Bob");
        User carol = new User("Carol");

        Group goa = new Group("Goa Trip");
        goa.addMember(alice.id());
        goa.addMember(bob.id());
        goa.addMember(carol.id());

        // 1. Equal split: Alice pays 12000 INR for hotel; split equally among 3
        var hotel = expenseSvc.create(goa.id(), alice.id(), "Hotel",
                Money.inr(12000), SplitMethod.EQUAL,
                List.of(new Payer(alice.id(), Money.inr(12000))),
                List.of(alice.id(), bob.id(), carol.id()),
                Map.of(), "exp-hotel");
        System.out.println("Created: " + hotel);
        for (var s : hotel.shares()) System.out.println("  share: " + s);

        // 2. Percent split: Bob pays 5000 INR for dinner; 50/30/20 (Alice, Bob, Carol)
        var dinner = expenseSvc.create(goa.id(), bob.id(), "Dinner",
                Money.inr(5000), SplitMethod.PERCENT,
                List.of(new Payer(bob.id(), Money.inr(5000))),
                List.of(alice.id(), bob.id(), carol.id()),
                Map.of("percents", List.of(new BigDecimal("50"), new BigDecimal("30"), new BigDecimal("20"))),
                "exp-dinner");
        System.out.println("Created: " + dinner);
        for (var s : dinner.shares()) System.out.println("  share: " + s);

        // 3. Exact split: Carol pays 1500 INR for taxi; Alice 800, Bob 400, Carol 300
        var taxi = expenseSvc.create(goa.id(), carol.id(), "Taxi",
                Money.inr(1500), SplitMethod.EXACT,
                List.of(new Payer(carol.id(), Money.inr(1500))),
                List.of(alice.id(), bob.id(), carol.id()),
                Map.of("amounts", List.of(Money.inr(800), Money.inr(400), Money.inr(300))),
                "exp-taxi");
        System.out.println("Created: " + taxi);

        // 4. Show balances  (positive = row user owes column user; negative = row user is owed)
        System.out.println("\n-- Balances --");
        printBalance("Alice", "Bob",   balances.balance(alice.id(), bob.id(),   goa.id(), "INR"));
        printBalance("Alice", "Carol", balances.balance(alice.id(), carol.id(), goa.id(), "INR"));
        printBalance("Bob",   "Carol", balances.balance(bob.id(),   carol.id(), goa.id(), "INR"));

        // 5. Debt simplification
        System.out.println("\n-- Suggested transfers (simplified) --");
        for (Transfer t : balances.simplifyGroup(goa.id(), "INR")) System.out.println(t);

        // 6. Settlement — detect direction, then settle the Bob<->Alice pair
        //
        //   balance(A, B) > 0  →  A owes B  →  A pays B
        //   balance(A, B) < 0  →  B owes A  →  B pays A
        //
        long netAliceBob = balances.balance(alice.id(), bob.id(), goa.id(), "INR");
        if (netAliceBob > 0) {
            // Alice owes Bob — Alice pays Bob
            balances.recordSettlement(alice.id(), bob.id(), goa.id(), Money.fromCents(netAliceBob, "INR"));
            System.out.println("\nAfter Alice pays Bob " + Money.fromCents(netAliceBob, "INR") + ":");
        } else if (netAliceBob < 0) {
            // Bob owes Alice — Bob pays Alice
            long bobOwesAlice = -netAliceBob;
            balances.recordSettlement(bob.id(), alice.id(), goa.id(), Money.fromCents(bobOwesAlice, "INR"));
            System.out.println("\nAfter Bob pays Alice " + Money.fromCents(bobOwesAlice, "INR") + ":");
        }
        printBalance("Alice", "Bob", balances.balance(alice.id(), bob.id(), goa.id(), "INR"));

        // 7. Edit expense — taxi was wrong; Carol actually paid 1800, Alice 1000, Bob 500, Carol 300
        System.out.println("\n-- Editing taxi expense (Carol paid extra 300) --");
        var updatedTaxi = expenseSvc.editExpense(
                taxi.id(),
                "Taxi (corrected)",
                Money.inr(1800),
                com.splitwise.domain.SplitMethod.EXACT,
                List.of(new Payer(carol.id(), Money.inr(1800))),
                List.of(alice.id(), bob.id(), carol.id()),
                Map.of("amounts", List.of(Money.inr(1000), Money.inr(500), Money.inr(300))),
                "exp-taxi-v2");
        System.out.println("Updated: " + updatedTaxi);
        for (var s : updatedTaxi.shares()) System.out.println("  share: " + s);
        System.out.println("Old taxi status: " + taxi.status() + " version=" + taxi.version());
        System.out.println("\n-- Balances after taxi edit --");
        printBalance("Alice", "Carol", balances.balance(alice.id(), carol.id(), goa.id(), "INR"));
        printBalance("Bob",   "Carol", balances.balance(bob.id(),   carol.id(), goa.id(), "INR"));

        // 8. Delete the dinner expense → balances reverse
        expenseSvc.deleteExpense(dinner.id());
        System.out.println("\n-- After deleting dinner --");
        printBalance("Alice", "Bob",   balances.balance(alice.id(), bob.id(),   goa.id(), "INR"));
        printBalance("Bob",   "Carol", balances.balance(bob.id(),   carol.id(), goa.id(), "INR"));
    }

    /**
     * Prints the balance between two users.
     *   positive → first user owes second user
     *   negative → second user owes first user
     */
    private static void printBalance(String nameA, String nameB, long cents) {
        if (cents > 0) {
            System.out.printf("%s owes %s: %s%n", nameA, nameB, Money.fromCents(cents, "INR"));
        } else if (cents < 0) {
            System.out.printf("%s owes %s: %s%n", nameB, nameA, Money.fromCents(-cents, "INR"));
        } else {
            System.out.printf("%s ↔ %s: settled%n", nameA, nameB);
        }
    }
}
