package com.snakeladder.rule;

import java.util.List;

public final class RulePacks {
    private RulePacks() {}

    public static RuleEngine traditionalIndia(int diceMax) {
        return new RuleEngine(List.of(
                new MustRollMaxToStartRule(diceMax),
                new StayOnOvershootRule()
        ));
    }

    public static RuleEngine casual() {
        return new RuleEngine(List.of(
                new StayOnOvershootRule()
        ));
    }

    public static RuleEngine strict(int diceMax) {
        return new RuleEngine(List.of(
                new MustRollMaxToStartRule(diceMax),
                new ExactFinishRule()
        ));
    }
}
