package com.authz.store;

import com.authz.api.GrantRule;
import com.authz.model.Resource;
import com.authz.model.Role;
import com.authz.model.User;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryAuthorizationStore implements AuthorizationStore {

    private final ConcurrentHashMap<String, Role>     roles     = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, User>     users     = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Resource> resources = new ConcurrentHashMap<>();

    @Override public void putRole(Role role)         { roles.put(role.id(), role); detectCycle(role.id()); }
    @Override public Optional<Role> getRole(String id) { return Optional.ofNullable(roles.get(id)); }

    @Override public void putUser(User user)         { users.put(user.id, user); }
    @Override public Optional<User> getUser(String id) { return Optional.ofNullable(users.get(id)); }

    @Override public void putResource(Resource r)    { resources.put(r.id, r); }
    @Override public Optional<Resource> getResource(String id) { return Optional.ofNullable(resources.get(id)); }

    @Override
    public List<GrantRule> effectiveGrantsFor(String userId) {
        User u = users.get(userId);
        if (u == null) return List.of();

        List<GrantRule> result = new ArrayList<>();
        for (String roleId : u.roleIds) {
            collectRoleGrants(roleId, result, new HashSet<>());
        }
        result.addAll(u.directGrants);
        return result;
    }

    private void collectRoleGrants(String roleId, List<GrantRule> out, Set<String> visited) {
        if (!visited.add(roleId)) return;
        Role r = roles.get(roleId);
        if (r == null) return;
        out.addAll(r.grants());
        if (r.parentRoleId() != null) {
            collectRoleGrants(r.parentRoleId(), out, visited);
        }
    }

    @Override
    public List<String> resourcePath(String resourceId) {
        List<String> path = new ArrayList<>();
        String cur = resourceId;
        while (cur != null) {
            path.add(cur);
            Resource r = resources.get(cur);
            if (r == null) break;
            cur = r.parentId;
        }
        return path;
    }

    private void detectCycle(String roleId) {
        Set<String> seen = new HashSet<>();
        String cur = roleId;
        while (cur != null) {
            if (!seen.add(cur)) {
                throw new IllegalStateException("cycle in role hierarchy at " + cur);
            }
            Role r = roles.get(cur);
            if (r == null) return;
            cur = r.parentRoleId();
        }
    }
}
