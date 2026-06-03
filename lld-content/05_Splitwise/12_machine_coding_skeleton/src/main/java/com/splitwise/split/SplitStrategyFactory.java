package com.splitwise.split;

import com.splitwise.domain.SplitMethod;

public final class SplitStrategyFactory {
    public SplitStrategy of(SplitMethod method) {
        return switch (method) {
            case EQUAL   -> new EqualSplit();
            case EXACT   -> new ExactSplit();
            case PERCENT -> new PercentSplit();
            case SHARE   -> new ShareSplit();
            default -> throw new UnsupportedOperationException("not implemented: " + method);
        };
    }
}
