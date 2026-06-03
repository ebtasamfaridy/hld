# 12 · Feature Flag System — Machine Coding Skeleton

In-process feature flag client with targeting rules, percentage rollouts (sticky), prerequisites, and evaluation reasons.

```
src/main/java/com/featureflags/
├── api/         FeatureFlags, FeatureFlagClient, EvaluationContext, EvaluationResult, Reason
├── core/        Flag, Variation, Rule, Condition, Prereq, Operator (sealed)
├── rule/        EqOperator, InOperator, PrefixOperator, RegexOperator, GtOperator
├── context/     (EvaluationContext lives in api; this folder kept for parity)
├── store/       FlagStore (interface), InMemoryFlagStore
├── client/      Evaluator, Bucketing, HashBucketing
└── Main.java
```

## Demo
1. Define flag `show-checkout` with rule:
   - country IN [IN, US] → on
   - tier == pro → 25% rollout to 'on'
   - else 'off'
2. Evaluate for 1 K simulated users; show rough percentage.
3. Show stickiness: same user, same result across calls.
4. Show subset preservation: expanding 25% → 50% only adds users.
5. Demonstrate prereq: `checkout-v2` requires `show-checkout=on`.
