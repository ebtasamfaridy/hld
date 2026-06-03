package com.featureflags.client;

public interface Bucketing {
    /** Returns 0..9999 for stable bucketing. */
    int bucket(String salt, String userId);
}
