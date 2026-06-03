package com.featureflags.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Flag {

    private final String key;
    private final String environment;
    private final boolean enabled;
    private final Map<String, Variation> variationsById;
    private final List<Rule> targetingRules;
    private final List<Prereq> prerequisites;
    private final String fallthroughVariationId;
    private final String offVariationId;
    private final long version;

    private Flag(Builder b) {
        this.key = b.key;
        this.environment = b.environment;
        this.enabled = b.enabled;
        this.targetingRules = List.copyOf(b.targetingRules);
        this.prerequisites = List.copyOf(b.prerequisites);
        this.fallthroughVariationId = b.fallthroughVariationId;
        this.offVariationId = b.offVariationId;
        this.version = b.version;
        Map<String, Variation> map = new HashMap<>();
        for (Variation v : b.variations) map.put(v.id(), v);
        this.variationsById = Map.copyOf(map);

        if (variationsById.get(fallthroughVariationId) == null)
            throw new IllegalArgumentException("fallthrough variation missing");
        if (variationsById.get(offVariationId) == null)
            throw new IllegalArgumentException("off variation missing");
    }

    public String key()                     { return key; }
    public String environment()             { return environment; }
    public boolean enabled()                { return enabled; }
    public List<Rule> targetingRules()      { return targetingRules; }
    public List<Prereq> prerequisites()     { return prerequisites; }
    public String fallthroughVariationId()  { return fallthroughVariationId; }
    public String offVariationId()          { return offVariationId; }
    public long version()                   { return version; }
    public Variation variation(String id)   { return variationsById.get(id); }
    public Map<String, Variation> variations() { return variationsById; }

    public static Builder builder(String key, String environment) {
        return new Builder(key, environment);
    }

    public static final class Builder {
        private final String key, environment;
        private boolean enabled = true;
        private List<Variation> variations = List.of();
        private List<Rule> targetingRules = List.of();
        private List<Prereq> prerequisites = List.of();
        private String fallthroughVariationId, offVariationId;
        private long version = 1;

        private Builder(String key, String env) { this.key = key; this.environment = env; }

        public Builder enabled(boolean v)                  { this.enabled = v; return this; }
        public Builder variations(List<Variation> v)       { this.variations = v; return this; }
        public Builder rules(List<Rule> r)                 { this.targetingRules = r; return this; }
        public Builder prerequisites(List<Prereq> p)       { this.prerequisites = p; return this; }
        public Builder fallthrough(String id)              { this.fallthroughVariationId = id; return this; }
        public Builder off(String id)                      { this.offVariationId = id; return this; }
        public Builder version(long v)                     { this.version = v; return this; }
        public Flag build()                                { return new Flag(this); }
    }
}
