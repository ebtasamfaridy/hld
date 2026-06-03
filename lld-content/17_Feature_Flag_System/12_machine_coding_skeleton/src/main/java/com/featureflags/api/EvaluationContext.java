package com.featureflags.api;

import java.util.HashMap;
import java.util.Map;

public final class EvaluationContext {

    private final String userId;
    private final Map<String, Object> attributes;

    private EvaluationContext(String userId, Map<String, Object> attributes) {
        this.userId = userId;
        this.attributes = Map.copyOf(attributes);
    }

    public String userId()                        { return userId; }
    public Map<String, Object> attributes()       { return attributes; }
    public Object get(String key)                 { return attributes.get(key); }

    public static Builder forUser(String userId) { return new Builder(userId); }

    public static final class Builder {
        private final String userId;
        private final Map<String, Object> attrs = new HashMap<>();
        private Builder(String userId) { this.userId = userId; }
        public Builder set(String key, Object value) { attrs.put(key, value); return this; }
        public EvaluationContext build()              { return new EvaluationContext(userId, attrs); }
    }
}
