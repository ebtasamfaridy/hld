package com.authz.api;

import com.authz.core.PolicyEvaluator;
import com.authz.engine.PermissionsCache;
import com.authz.store.AuthorizationStore;

public final class AuthorizationService {

    private final AuthorizationStore store;
    private final PermissionsCache cache;
    private final PolicyEvaluator evaluator = new PolicyEvaluator();

    public AuthorizationService(AuthorizationStore store) {
        this.store = store;
        this.cache = new PermissionsCache(store);
    }

    public boolean can(String userId, String action, String resourceId) {
        return decide(userId, action, resourceId) == Decision.ALLOW;
    }

    public Decision decide(String userId, String action, String resourceId) {
        var grants    = cache.grantsFor(userId);
        var ancestors = cache.ancestorsOf(resourceId);
        return evaluator.decide(action, ancestors, grants);
    }

    public void invalidateUser(String userId)         { cache.invalidateUser(userId); }
    public void invalidateResource(String resourceId) { cache.invalidateResource(resourceId); }
    public void invalidateAll()                        { cache.invalidateAll(); }

    public AuthorizationStore store() { return store; }
}
