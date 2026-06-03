package com.authz.model;

public final class Resource {
    public final String id;        // e.g., "doc:42", "folder:5"
    public final String parentId;  // nullable

    public Resource(String id, String parentId) { this.id = id; this.parentId = parentId; }
}
