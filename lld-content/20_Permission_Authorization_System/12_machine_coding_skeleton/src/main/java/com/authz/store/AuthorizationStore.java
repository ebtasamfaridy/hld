package com.authz.store;

import com.authz.api.GrantRule;
import com.authz.model.Resource;
import com.authz.model.Role;
import com.authz.model.User;

import java.util.List;
import java.util.Optional;

public interface AuthorizationStore {

    void putRole(Role role);
    Optional<Role> getRole(String roleId);

    void putUser(User user);
    Optional<User> getUser(String userId);

    void putResource(Resource resource);
    Optional<Resource> getResource(String resourceId);

    /** Build the union of grants from user.roleIds (with hierarchy) + user.directGrants. */
    List<GrantRule> effectiveGrantsFor(String userId);

    /** Walk the resource hierarchy from leaf to root. */
    List<String> resourcePath(String resourceId);
}
