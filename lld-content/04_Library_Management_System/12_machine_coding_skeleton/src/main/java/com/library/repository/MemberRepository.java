package com.library.repository;

import com.library.domain.Member;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MemberRepository {
    private final ConcurrentMap<UUID, Member> byId = new ConcurrentHashMap<>();

    public Member save(Member m) { byId.put(m.id(), m); return m; }
    public Optional<Member> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }
}
