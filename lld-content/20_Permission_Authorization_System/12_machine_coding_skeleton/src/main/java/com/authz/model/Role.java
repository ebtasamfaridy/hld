package com.authz.model;

import com.authz.api.GrantRule;

import java.util.ArrayList;
import java.util.List;

public final class Role {

    private final String id;
    private final String name;
    private final String parentRoleId;          // nullable
    private final List<GrantRule> grants = new ArrayList<>();

    public Role(String id, String name, String parentRoleId) {
        this.id = id; this.name = name; this.parentRoleId = parentRoleId;
    }
    public String id()           { return id; }
    public String name()         { return name; }
    public String parentRoleId() { return parentRoleId; }
    public List<GrantRule> grants() { return grants; }
    public Role addGrant(GrantRule g) { grants.add(g); return this; }
}
