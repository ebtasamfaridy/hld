package com.ratelimit.middleware;

import com.ratelimit.domain.RateKey;
import com.ratelimit.domain.Request;

import java.util.ArrayList;
import java.util.List;

public final class CompositeKeyExtractor implements KeyExtractor {
    private final boolean perIp;
    private final boolean perUser;
    private final boolean perRoute;

    public CompositeKeyExtractor(boolean perIp, boolean perUser, boolean perRoute) {
        this.perIp = perIp; this.perUser = perUser; this.perRoute = perRoute;
    }

    @Override
    public List<RateKey> keysFor(Request r) {
        List<RateKey> out = new ArrayList<>();
        if (perIp && r.ip() != null) out.add(new RateKey("ip", r.ip()));
        if (perUser && r.userId() != null) out.add(new RateKey("user", r.userId()));
        if (perRoute && r.route() != null) out.add(new RateKey("route", r.route()));
        return out;
    }
}
