package com.ratelimit.domain;

public record RateKey(String family, String value) {
    public String storeKey() { return "rl:" + family + ":" + value; }
}
