package com.carrental.payment;

import com.carrental.domain.Money;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Scriptable in-memory gateway for the demo. Idempotent on per-key basis. */
public final class FakeGateway implements PaymentGateway {

    private final ConcurrentMap<String, AuthResult>    auths    = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CaptureResult> captures = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, RefundResult>  refunds  = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ChargeResult>  charges  = new ConcurrentHashMap<>();
    private final Set<String> voidedAuths = new HashSet<>();

    private volatile String nextAuthFailure = null;
    private volatile String nextMitFailure = null;

    public void scriptNextAuthFailure(String reason) { this.nextAuthFailure = reason; }
    public void scriptNextMitFailure(String reason)  { this.nextMitFailure = reason; }

    @Override public AuthResult authorize(UUID resvId, Money amount, String idempotencyKey) {
        return auths.computeIfAbsent(idempotencyKey, k -> {
            if (nextAuthFailure != null) {
                String r = nextAuthFailure; nextAuthFailure = null;
                return new AuthResult(false, null, r);
            }
            return new AuthResult(true, "AUTH-" + UUID.randomUUID(), null);
        });
    }

    @Override public CaptureResult capture(String authId, Money amount, String idempotencyKey) {
        if (voidedAuths.contains(authId))
            return new CaptureResult(false, null, "auth was voided");
        return captures.computeIfAbsent(idempotencyKey,
                k -> new CaptureResult(true, "CAP-" + UUID.randomUUID(), null));
    }

    @Override public synchronized boolean voidAuth(String authId) { return voidedAuths.add(authId); }

    @Override public RefundResult refund(String captureId, Money amount, String idempotencyKey) {
        return refunds.computeIfAbsent(idempotencyKey,
                k -> new RefundResult(true, "REF-" + UUID.randomUUID(), null));
    }

    @Override public ChargeResult mit(String savedMethodId, Money amount, String idempotencyKey) {
        return charges.computeIfAbsent(idempotencyKey, k -> {
            if (nextMitFailure != null) {
                String r = nextMitFailure; nextMitFailure = null;
                return new ChargeResult(false, null, r);
            }
            return new ChargeResult(true, "MIT-" + UUID.randomUUID(), null);
        });
    }

    public Map<String, AuthResult> authSnapshot()    { return Map.copyOf(auths); }
    public Map<String, CaptureResult> captureSnapshot() { return Map.copyOf(captures); }
}
