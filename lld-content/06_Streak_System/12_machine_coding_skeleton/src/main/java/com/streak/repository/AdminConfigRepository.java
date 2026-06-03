package com.streak.repository;

import com.streak.domain.AdminConfig;

public interface AdminConfigRepository {
    AdminConfig get();

    /** CAS on version. Returns true if write succeeded. */
    boolean saveWithCas(AdminConfig updated);
}
