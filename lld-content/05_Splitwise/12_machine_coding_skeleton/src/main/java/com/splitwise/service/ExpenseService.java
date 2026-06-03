package com.splitwise.service;

import com.splitwise.domain.Expense;
import com.splitwise.domain.ExpenseShare;
import com.splitwise.domain.Money;
import com.splitwise.domain.Payer;
import com.splitwise.domain.SplitMethod;
import com.splitwise.split.SplitStrategyFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ExpenseService {
    private final ConcurrentMap<UUID, Expense> store = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> idemIndex = new ConcurrentHashMap<>();
    private final BalanceService balances;
    private final SplitStrategyFactory factory;

    public ExpenseService(BalanceService balances, SplitStrategyFactory factory) {
        this.balances = balances;
        this.factory = factory;
    }

    public Expense create(UUID groupId, UUID createdBy, String description,
                          Money amount, SplitMethod method, List<Payer> payers,
                          List<UUID> participants, Map<String, Object> splitConfig,
                          String idempotencyKey) {
        UUID existing = idemIndex.get(idempotencyKey);
        if (existing != null) return store.get(existing);

        List<ExpenseShare> shares = factory.of(method).compute(amount, participants, splitConfig);
        Expense e = new Expense(groupId, createdBy, description, amount, method, payers, shares,
                                Instant.now(), idempotencyKey);
        store.put(e.id(), e);
        idemIndex.putIfAbsent(idempotencyKey, e.id());
        balances.applyExpense(e);
        return e;
    }

    /**
     * Edit an existing active expense.
     * Reverses the old expense's balance effects, marks it EDITED,
     * then creates a replacement expense and applies its effects.
     */
    public Expense editExpense(UUID oldId, String newDescription,
                               Money newAmount, SplitMethod newMethod,
                               List<Payer> newPayers, List<UUID> newParticipants,
                               Map<String, Object> splitConfig,
                               String newIdempotencyKey) {
        Expense old = store.get(oldId);
        if (old == null || old.status() == Expense.Status.DELETED) {
            throw new IllegalArgumentException("expense not found or already deleted: " + oldId);
        }
        // Idempotency: if the replacement key was already processed, return it.
        UUID existingId = idemIndex.get(newIdempotencyKey);
        if (existingId != null) return store.get(existingId);

        // Reverse old balance effects first, then mark edited.
        balances.reverseExpense(old);
        old.markEdited();

        // Build and persist the replacement expense.
        List<ExpenseShare> shares = factory.of(newMethod).compute(newAmount, newParticipants, splitConfig);
        Expense updated = new Expense(old.groupId(), old.createdBy(), newDescription,
                                      newAmount, newMethod, newPayers, shares,
                                      Instant.now(), newIdempotencyKey);
        store.put(updated.id(), updated);
        idemIndex.putIfAbsent(newIdempotencyKey, updated.id());
        balances.applyExpense(updated);
        return updated;
    }

    public void deleteExpense(UUID id) {
        Expense e = store.get(id);
        if (e == null) return;
        balances.reverseExpense(e);
        e.delete();
    }

    public Map<UUID, Expense> all() { return Map.copyOf(store); }
}
