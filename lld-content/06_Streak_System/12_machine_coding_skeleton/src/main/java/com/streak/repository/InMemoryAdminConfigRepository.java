package com.streak.repository;

import com.streak.domain.AdminConfig;

import java.util.concurrent.atomic.AtomicReference;

public final class InMemoryAdminConfigRepository implements AdminConfigRepository {

    private final AtomicReference<AdminConfig> ref =
            new AtomicReference<>(AdminConfig.defaults());

    @Override public AdminConfig get() { return ref.get(); }

    @Override
    public boolean saveWithCas(AdminConfig updated) {
        AdminConfig current = ref.get();
        long expectedPrev = updated.version() - 1;
        if (current.version() != expectedPrev) return false;
        return ref.compareAndSet(current, updated);
    }
}
