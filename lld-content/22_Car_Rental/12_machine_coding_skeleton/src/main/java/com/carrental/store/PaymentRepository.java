package com.carrental.store;

import com.carrental.payment.Payment;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PaymentRepository {
    private final ConcurrentMap<UUID, Payment> byId = new ConcurrentHashMap<>();
    public Payment save(Payment p) { byId.put(p.id(), p); return p; }
    public Optional<Payment> byId(UUID id) { return Optional.ofNullable(byId.get(id)); }
}
