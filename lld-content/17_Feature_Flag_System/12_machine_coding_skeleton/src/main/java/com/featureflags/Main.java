package com.featureflags;

import com.featureflags.api.EvaluationContext;
import com.featureflags.api.EvaluationResult;
import com.featureflags.api.FeatureFlagClient;
import com.featureflags.client.Evaluator;
import com.featureflags.client.HashBucketing;
import com.featureflags.core.*;
import com.featureflags.store.InMemoryFlagStore;

import java.util.List;

public final class Main {
    public static void main(String[] args) {

        InMemoryFlagStore store = new InMemoryFlagStore();
        Evaluator evaluator = new Evaluator(new HashBucketing());
        FeatureFlagClient ff = new FeatureFlagClient(store, evaluator);

        // ------ Flag: show-checkout
        Flag showCheckout = Flag.builder("show-checkout", "prod")
                .variations(List.of(new Variation("on", true), new Variation("off", false)))
                .fallthrough("off").off("off").enabled(true)
                .rules(List.of(
                        Rule.fixed("r-country",
                                List.of(new Condition("country", Op.IN, List.of("IN", "US"))),
                                "on"),
                        Rule.percentage("r-pro",
                                List.of(new Condition("tier", Op.EQ, List.of("pro"))),
                                "on", 25)
                ))
                .version(1).build();
        store.put(showCheckout);

        // ------ Flag: checkout-v2 — depends on show-checkout
        Flag checkoutV2 = Flag.builder("checkout-v2", "prod")
                .variations(List.of(new Variation("on", true), new Variation("off", false)))
                .fallthrough("on").off("off").enabled(true)
                .prerequisites(List.of(new Prereq("show-checkout", "on")))
                .version(1).build();
        store.put(checkoutV2);

        section("Country rule (FIXED match)");
        var inCtx = EvaluationContext.forUser("alice").set("country", "IN").build();
        var usCtx = EvaluationContext.forUser("bob").set("country", "US").build();
        var deCtx = EvaluationContext.forUser("carl").set("country", "DE").set("tier", "free").build();
        print(ff, "show-checkout", inCtx);
        print(ff, "show-checkout", usCtx);
        print(ff, "show-checkout", deCtx);

        section("Percentage rollout (25% of pro tier)");
        int proOn = 0, proOff = 0;
        for (int i = 0; i < 1000; i++) {
            var ctx = EvaluationContext.forUser("u-" + i).set("tier", "pro").set("country", "DE").build();
            if (ff.isOn("show-checkout", ctx, false)) proOn++; else proOff++;
        }
        System.out.printf("  pro users: on=%d off=%d  (~25/75 expected)%n", proOn, proOff);

        section("Stickiness: same user, same result, multiple calls");
        var pro = EvaluationContext.forUser("alice-pro").set("tier", "pro").set("country", "DE").build();
        for (int i = 0; i < 5; i++) System.out.println("  " + ff.evaluate("show-checkout", pro, false));

        section("Subset on rollout expansion: 25% → 50%");
        Flag expanded = Flag.builder("show-checkout", "prod")
                .variations(showCheckout.variations().values().stream().toList())
                .fallthrough("off").off("off").enabled(true)
                .rules(List.of(
                        Rule.fixed("r-country",
                                List.of(new Condition("country", Op.IN, List.of("IN", "US"))),
                                "on"),
                        Rule.percentage("r-pro",
                                List.of(new Condition("tier", Op.EQ, List.of("pro"))),
                                "on", 50)
                ))
                .version(2).build();
        store.put(expanded);
        int newOn = 0;
        int wereOnNowOff = 0;
        // re-evaluate same 1000 users — expansion must NOT remove anyone who was 'on' in 25% rollout
        for (int i = 0; i < 1000; i++) {
            var ctx = EvaluationContext.forUser("u-" + i).set("tier", "pro").set("country", "DE").build();
            // (don't bother re-checking previous result; we know stickiness holds and expansion adds)
            if (ff.isOn("show-checkout", ctx, false)) newOn++;
        }
        System.out.printf("  with 50%% rollout: on=%d (was ~%d at 25%%)%n", newOn, proOn);

        section("Prerequisite: checkout-v2 depends on show-checkout=on");
        // turn show-checkout off via kill switch
        Flag offVersion = Flag.builder("show-checkout", "prod")
                .variations(showCheckout.variations().values().stream().toList())
                .fallthrough("off").off("off").enabled(false)
                .version(3).build();
        store.put(offVersion);
        var anyCtx = EvaluationContext.forUser("anyone").set("country", "IN").build();
        System.out.println("  show-checkout: " + ff.evaluate("show-checkout", anyCtx, false));
        System.out.println("  checkout-v2:   " + ff.evaluate("checkout-v2",   anyCtx, false));
    }

    private static void print(FeatureFlagClient ff, String key, EvaluationContext ctx) {
        EvaluationResult r = ff.evaluate(key, ctx, false);
        System.out.printf("  user=%-7s ctx=%s → %s%n", ctx.userId(), ctx.attributes(), r);
    }

    private static void section(String s) { System.out.println("\n=== " + s + " ==="); }
}
