package com.featureflags.core;

import com.featureflags.api.EvaluationContext;

import java.util.List;

public final class Rule {

    public enum Kind { FIXED, PERCENTAGE }

    private final String id;
    private final List<Condition> conditions;        // ANDed
    private final Kind kind;
    private final String variationId;                // FIXED
    private final Integer rolloutPercentage;          // PERCENTAGE 0..100

    private Rule(String id, List<Condition> conditions, Kind kind,
                 String variationId, Integer rolloutPercentage) {
        this.id = id;
        this.conditions = List.copyOf(conditions);
        this.kind = kind;
        this.variationId = variationId;
        this.rolloutPercentage = rolloutPercentage;
    }

    public static Rule fixed(String id, List<Condition> cs, String variationId) {
        return new Rule(id, cs, Kind.FIXED, variationId, null);
    }
    public static Rule percentage(String id, List<Condition> cs, String variationId, int percentage) {
        if (percentage < 0 || percentage > 100) throw new IllegalArgumentException("0..100");
        return new Rule(id, cs, Kind.PERCENTAGE, variationId, percentage);
    }

    public boolean conditionsMatch(EvaluationContext ctx) {
        for (Condition c : conditions) if (!c.matches(ctx)) return false;
        return true;
    }

    public String id()                   { return id; }
    public Kind kind()                   { return kind; }
    public String variationId()          { return variationId; }
    public Integer rolloutPercentage()   { return rolloutPercentage; }
}
