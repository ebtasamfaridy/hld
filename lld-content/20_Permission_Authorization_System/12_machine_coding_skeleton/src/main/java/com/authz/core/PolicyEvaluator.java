package com.authz.core;

import com.authz.api.Decision;
import com.authz.api.GrantRule;

import java.util.List;

public final class PolicyEvaluator {

    /**
     * DENY > ALLOW.
     * Default: DENY.
     * Walk the resource path leaf → root. At each level:
     *   - If a DENY rule matches, return DENY (short-circuit).
     *   - If an ALLOW rule matches, flag and continue walking (looking for DENYs).
     */
    public Decision decide(String action, List<String> resourcePath, List<GrantRule> grants) {
        boolean sawAllow = false;
        for (String r : resourcePath) {
            for (GrantRule g : grants) {
                if (!g.matches(action, r)) continue;
                if (g.decision == Decision.DENY) return Decision.DENY;
                sawAllow = true;
            }
        }
        return sawAllow ? Decision.ALLOW : Decision.DENY;
    }
}
