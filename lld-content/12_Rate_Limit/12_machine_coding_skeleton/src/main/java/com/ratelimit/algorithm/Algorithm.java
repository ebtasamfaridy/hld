package com.ratelimit.algorithm;

import com.ratelimit.domain.Decision;
import com.ratelimit.domain.LimitConfig;
import com.ratelimit.domain.RateKey;

public interface Algorithm {
    Decision check(RateKey key, LimitConfig config, long cost, long nowMs);
}
