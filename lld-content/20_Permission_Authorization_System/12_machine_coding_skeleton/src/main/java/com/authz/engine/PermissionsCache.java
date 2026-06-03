package com.authz.engine;

import com.authz.api.GrantRule;
import com.authz.store.AuthorizationStore;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class PermissionsCache {

    private final AuthorizationStore store;
    private final ConcurrentHashMap<String, List<GrantRule>> userGrants = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<String>>    ancestors  = new ConcurrentHashMap<>();

    public PermissionsCache(AuthorizationStore store) { this.store = store; }

    public List<GrantRule> grantsFor(String userId) {
        return userGrants.computeIfAbsent(userId, store::effectiveGrantsFor);
    }

    public List<String> ancestorsOf(String resourceId) {
        return ancestors.computeIfAbsent(resourceId, store::resourcePath);
    }

    public void invalidateUser(String userId)         { userGrants.remove(userId); }
    public void invalidateResource(String resourceId) { ancestors.remove(resourceId); }

    public void invalidateAll() { userGrants.clear(); ancestors.clear(); }
}
