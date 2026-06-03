package com.ratelimit.middleware;

import com.ratelimit.domain.RateKey;
import com.ratelimit.domain.Request;

import java.util.List;

public interface KeyExtractor {
    /** Returns the keys (scopes) to evaluate for this request. May be empty if no limits apply. */
    List<RateKey> keysFor(Request r);
}
