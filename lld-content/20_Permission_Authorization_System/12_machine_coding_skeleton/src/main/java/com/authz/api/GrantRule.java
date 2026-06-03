package com.authz.api;

import com.authz.policy.WildcardMatcher;

public final class GrantRule {

    public final String action;            // "read" | "write" | "*"
    public final String resourcePattern;   // "doc:42" | "doc:*" | "folder:5/*" | "*"
    public final Decision decision;

    public GrantRule(String action, String resourcePattern, Decision decision) {
        this.action = action;
        this.resourcePattern = resourcePattern;
        this.decision = decision;
    }

    public boolean matches(String act, String resourceId) {
        return WildcardMatcher.match(action, act)
            && WildcardMatcher.match(resourcePattern, resourceId);
    }

    @Override public String toString() {
        return decision + " " + action + " on " + resourcePattern;
    }
}
