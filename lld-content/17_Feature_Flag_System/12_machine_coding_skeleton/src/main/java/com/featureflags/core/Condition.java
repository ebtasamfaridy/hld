package com.featureflags.core;

import com.featureflags.api.EvaluationContext;

import java.util.List;
import java.util.regex.Pattern;

public final class Condition {

    private final String attribute;
    private final Op op;
    private final List<Object> values;
    private final Pattern compiledRegex;       // null unless op==REGEX

    public Condition(String attribute, Op op, List<Object> values) {
        this.attribute = attribute;
        this.op = op;
        this.values = List.copyOf(values);
        this.compiledRegex = (op == Op.REGEX && !values.isEmpty())
                ? Pattern.compile(values.get(0).toString())
                : null;
    }

    public boolean matches(EvaluationContext ctx) {
        Object actual = "userId".equals(attribute) ? ctx.userId() : ctx.get(attribute);
        if (actual == null) return op == Op.NOT_IN || op == Op.NEQ;
        return switch (op) {
            case EQ        -> actual.equals(values.get(0));
            case NEQ       -> !actual.equals(values.get(0));
            case IN        -> values.contains(actual);
            case NOT_IN    -> !values.contains(actual);
            case PREFIX    -> actual.toString().startsWith(values.get(0).toString());
            case SUFFIX    -> actual.toString().endsWith(values.get(0).toString());
            case CONTAINS  -> actual.toString().contains(values.get(0).toString());
            case REGEX     -> compiledRegex.matcher(actual.toString()).find();
            case GT        -> compareNum(actual, values.get(0)) > 0;
            case LT        -> compareNum(actual, values.get(0)) < 0;
        };
    }

    private static int compareNum(Object a, Object b) {
        double x = a instanceof Number n ? n.doubleValue() : Double.parseDouble(a.toString());
        double y = b instanceof Number n ? n.doubleValue() : Double.parseDouble(b.toString());
        return Double.compare(x, y);
    }

    public String attribute() { return attribute; }
    public Op op()            { return op; }
}
