package com.carrental.domain;

import java.util.UUID;

public final class Ids {
    private Ids() {}
    public static UUID newId() { return UUID.randomUUID(); }
    public static String shortHex(UUID id) { return id.toString().substring(0, 8); }
}
