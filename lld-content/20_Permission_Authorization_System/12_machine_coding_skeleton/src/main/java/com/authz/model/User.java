package com.authz.model;

import com.authz.api.GrantRule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class User {
    public final String id;
    public final String tenantId;
    public final Set<String> roleIds = new HashSet<>();
    public final List<GrantRule> directGrants = new ArrayList<>();

    public User(String id, String tenantId) { this.id = id; this.tenantId = tenantId; }
}
