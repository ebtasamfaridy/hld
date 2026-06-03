package com.splitwise.service;

import com.splitwise.domain.Expense;
import com.splitwise.domain.ExpenseShare;
import com.splitwise.domain.Money;
import com.splitwise.domain.Payer;
import com.splitwise.simplify.DebtSimplifier;
import com.splitwise.simplify.Transfer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Maintains in-memory pair balances. Positive net means user_a owes user_b.
 * Pair key always has user_a < user_b (canonical ordering).
 */
public final class BalanceService {
    private record PairKey(UUID a, UUID b, UUID groupId, String currency) {}

    private final ConcurrentMap<PairKey, Long> netCents = new ConcurrentHashMap<>();
    private final DebtSimplifier simplifier = new DebtSimplifier();

    /** Apply expense: each participant owes their share to each payer proportionally. */
    public void applyExpense(Expense e) {
        applyDelta(e, +1);
    }
    public void reverseExpense(Expense e) {
        applyDelta(e, -1);
    }

    private void applyDelta(Expense e, int sign) {
        Money total = e.amount();
        for (Payer payer : e.payers()) {
            for (ExpenseShare share : e.shares()) {
                if (payer.userId().equals(share.userId())) continue;
                // owed-from-share to payer is proportional: share × (payer.amount / total)
                long delta = share.owedAmount().cents() * payer.amount().cents() / total.cents();
                addToPair(share.userId(), payer.userId(), e.groupId(), total.currency(), delta * sign);
            }
        }
    }

    private void addToPair(UUID owesUser, UUID owedUser, UUID groupId, String currency, long delta) {
        UUID a = owesUser.compareTo(owedUser) < 0 ? owesUser : owedUser;
        UUID b = a.equals(owesUser) ? owedUser : owesUser;
        long signed = a.equals(owesUser) ? delta : -delta;
        var key = new PairKey(a, b, groupId, currency);
        netCents.merge(key, signed, Long::sum);
    }

    public long balance(UUID userA, UUID userB, UUID groupId, String currency) {
        UUID a = userA.compareTo(userB) < 0 ? userA : userB;
        UUID b = a.equals(userA) ? userB : userA;
        long c = netCents.getOrDefault(new PairKey(a, b, groupId, currency), 0L);
        // return signed for the perspective of userA (positive = userA owes userB)
        return a.equals(userA) ? c : -c;
    }

    public void recordSettlement(UUID payerId, UUID payeeId, UUID groupId, Money amount) {
        // payer pays payee; reduces payer's debt to payee
        addToPair(payerId, payeeId, groupId, amount.currency(), -amount.cents());
    }

    /**
     * Compute net per-user balance for a given group + currency, then run simplification.
     */
    public List<Transfer> simplifyGroup(UUID groupId, String currency) {
        Map<UUID, Long> net = new HashMap<>();
        for (var entry : netCents.entrySet()) {
            PairKey k = entry.getKey();
            if (!Objects.equals(k.groupId(), groupId)) continue;
            if (!k.currency().equals(currency)) continue;
            long c = entry.getValue();
            // c = how much A owes B (positive). So B should receive c, A should pay c.
            net.merge(k.a(), -c, Long::sum);
            net.merge(k.b(), +c, Long::sum);
        }
        return simplifier.simplify(net, currency);
    }

    public Map<PairKey, Long> snapshot() { return Map.copyOf(netCents); }
}
