# 09 · Feature Flag System — State Machines

## Flag lifecycle (admin view)

```mermaid
stateDiagram-v2
    [*] --> DRAFT
    DRAFT --> ACTIVE : publish (first push to env)
    ACTIVE --> ACTIVE : update (rules, percentage, variations)
    ACTIVE --> DISABLED : kill switch flipped (off)
    DISABLED --> ACTIVE : kill switch flipped (on)
    ACTIVE --> ARCHIVED : delete (soft)
    DISABLED --> ARCHIVED : delete (soft)
    ARCHIVED --> ACTIVE : restore
    ARCHIVED --> [*]
```

`DISABLED` is a soft state — the kill switch — meant for incident response. `ARCHIVED` is the end-of-life state.

## Rollout lifecycle (operational view)

```mermaid
stateDiagram-v2
    [*] --> DARK : 0% rollout
    DARK --> CANARY : 1-5%
    CANARY --> PARTIAL : 10-50%
    PARTIAL --> FULL : 100%
    FULL --> [*]
    CANARY --> DARK : rollback
    PARTIAL --> CANARY : rollback (still partial)
    PARTIAL --> DARK   : rollback to off
```

This is operational, not enforced by the system. The system supports any 0–100 value; teams use stages for hygiene.

## Subscriber state (SDK)

```mermaid
stateDiagram-v2
    [*] --> BOOTSTRAPPING : load CDN snapshot
    BOOTSTRAPPING --> CONNECTING : try SSE
    CONNECTING --> STREAMING : connected
    STREAMING --> RECONNECTING : connection lost / heartbeat timeout
    RECONNECTING --> STREAMING : reconnected
    RECONNECTING --> POLLING_FALLBACK : repeated failures
    POLLING_FALLBACK --> CONNECTING : retry
    STREAMING --> [*] : stop()
    BOOTSTRAPPING --> FAILED : CDN unreachable AND no cached snapshot
    FAILED --> [*]
```

## Evaluation result reasons

```mermaid
stateDiagram-v2
    [*] --> EVALUATING
    EVALUATING --> OFF : kill switch off
    EVALUATING --> PREREQUISITE_FAILED : prereq mismatch
    EVALUATING --> RULE_MATCH : a rule matched
    EVALUATING --> FALLTHROUGH : no rule matched
    EVALUATING --> DEFAULT : flag missing / SDK error
    OFF --> [*]
    PREREQUISITE_FAILED --> [*]
    RULE_MATCH --> [*]
    FALLTHROUGH --> [*]
    DEFAULT --> [*]
```

These reasons are surfaced in `EvaluationResult.reason` for diagnostics.

## Output

```
Flag:        DRAFT → ACTIVE ↔ DISABLED → ARCHIVED ↔ restored
Rollout:     DARK → CANARY → PARTIAL → FULL (operational stages)
Subscriber:  BOOTSTRAPPING → STREAMING ↔ RECONNECTING → POLLING_FALLBACK
Eval result: OFF / PREREQUISITE_FAILED / RULE_MATCH / FALLTHROUGH / DEFAULT
```
