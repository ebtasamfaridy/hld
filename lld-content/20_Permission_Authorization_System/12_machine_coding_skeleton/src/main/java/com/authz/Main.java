package com.authz;

import com.authz.api.AuthorizationService;
import com.authz.api.Decision;
import com.authz.api.GrantRule;
import com.authz.model.Resource;
import com.authz.model.Role;
import com.authz.model.User;
import com.authz.store.InMemoryAuthorizationStore;

public final class Main {
    public static void main(String[] args) {
        var store = new InMemoryAuthorizationStore();

        Role viewer = new Role("r-viewer", "viewer", null);
        viewer.addGrant(new GrantRule("read", "doc:*", Decision.ALLOW));
        viewer.addGrant(new GrantRule("read", "folder:*", Decision.ALLOW));

        Role editor = new Role("r-editor", "editor", "r-viewer");
        editor.addGrant(new GrantRule("write", "doc:*", Decision.ALLOW));

        Role admin = new Role("r-admin", "admin", "r-editor");
        admin.addGrant(new GrantRule("delete", "*", Decision.ALLOW));

        store.putRole(viewer);
        store.putRole(editor);
        store.putRole(admin);

        store.putResource(new Resource("project:1", null));
        store.putResource(new Resource("folder:5", "project:1"));
        store.putResource(new Resource("doc:42", "folder:5"));
        store.putResource(new Resource("doc:99", "folder:5"));

        User alice = new User("alice", "t-1");
        alice.roleIds.add("r-admin");

        User bob = new User("bob", "t-1");
        bob.roleIds.add("r-editor");
        // Direct DENY: bob cannot read anything in folder:5
        bob.directGrants.add(new GrantRule("read", "folder:5/*", Decision.DENY));

        User carl = new User("carl", "t-1");
        carl.roleIds.add("r-viewer");

        store.putUser(alice); store.putUser(bob); store.putUser(carl);

        var authz = new AuthorizationService(store);

        section("alice (admin)");
        check(authz, "alice", "read",   "doc:42");
        check(authz, "alice", "write",  "doc:42");
        check(authz, "alice", "delete", "doc:42");
        check(authz, "alice", "read",   "folder:5");

        section("bob (editor; explicitly DENIED on folder:5/*)");
        check(authz, "bob", "write",  "doc:42");   // editor can write
        check(authz, "bob", "read",   "doc:42");   // DENY at parent folder
        check(authz, "bob", "read",   "doc:99");   // DENY at parent folder
        check(authz, "bob", "delete", "doc:42");   // not granted → DENY

        section("carl (viewer)");
        check(authz, "carl", "read",   "doc:42");
        check(authz, "carl", "write",  "doc:42");

        section("Cache invalidation: revoke alice's admin → editor only");
        alice.roleIds.clear();
        alice.roleIds.add("r-editor");
        authz.invalidateUser("alice");
        check(authz, "alice", "delete", "doc:42");   // now DENY
        check(authz, "alice", "write",  "doc:42");   // still ALLOW
    }

    private static void check(AuthorizationService authz, String user, String act, String res) {
        Decision d = authz.decide(user, act, res);
        System.out.printf("  %-5s %-7s %-15s → %s%n", user, act, res, d);
    }
    private static void section(String s) { System.out.println("\n=== " + s + " ==="); }
}
