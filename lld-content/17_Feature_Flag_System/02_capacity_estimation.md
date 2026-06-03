# 02 · Feature Flag System — Capacity Estimation

## Scale

```
Customers (tenants):        10 K
Avg apps per tenant:        5
Concurrent SDK clients:     500 K (servers + mobile)
Active flags:               20 K
Flag evaluations / sec:     20 M (every API request hits ~10 flags)
Admin writes / sec:         50
```

## Why local evaluation is mandatory

If every `isOn(key)` call hit a remote service:
- 20 M req/sec × 1 KB request = 20 GB/s — absurd.
- 1 ms RTT × every request × 10 flags / req = 10 ms latency tax per API call.

Solution: **the SDK evaluates locally**. The server pushes (or polls) the **flag config** to each SDK; eval happens in-memory.

Math after this:
- Each SDK polls/streams config: a few hundred bytes per flag × 20 K flags = ~5 MB. Compressed ~1 MB.
- Updates only push the **changed flag** (a few hundred bytes).
- Total bus traffic: 50 writes/sec × 500 K subscribers × 500 B = 12.5 GB/s — also too much. Mitigate via **fan-out hierarchy**: one connection per pop → fan to local subscribers.

## Storage

```
Flags table:             20 K rows × 5 KB = 100 MB
Audit log:               50 writes/sec × 86400 × 365 = 1.5 B rows over a year
                          × 1 KB = 1.5 TB/year (compressible)
Audit cold storage:      S3, partitioned by month
```

## Bucketing math

Bucketing must satisfy:
1. Same `(flagKey, userId)` → same bucket forever.
2. Bucket assignment is **uniform** across users.
3. Increasing percentage from 10% to 50% only **adds** users; never moves an existing 10% user out.

Algorithm: `bucket = hash(flagKey + ":" + userId) mod 10000` → percentage check.

```
salt:    flagKey
hash:    SHA1 or murmur3
bucket:  hash(salt:userId) mod 10000   (0..9999)
include: bucket < (percentage * 100)
```

A user in the 10% rollout is included if bucket < 1000. When we expand to 50%, they're still included (bucket < 5000). Subset preservation guaranteed.

## Hot ops

| Op | Cost | Where |
| --- | --- | --- |
| `isOn(flag, ctx)` | <1 µs | In-process SDK; no network |
| Admin update | ~50 ms | Validate → DB → publish |
| SDK initial fetch | ~100 ms | Pull all flags for env |
| SDK incremental update | ~10 ms | Apply patch from stream |

## What forces design

1. **Local evaluation** — only way to hit <1 ms.
2. **Push updates** — keeps SDKs fresh in seconds, not minutes.
3. **Stable bucketing** — same user always same bucket.
4. **Subset semantics on rollout expansion** — gradual rollouts don't shuffle users.
5. **Audit log** — every change preserved; replayable.

## Output

```
Scale:        500K SDK clients, 20K flags, 20M evals/sec
Architecture: server holds truth; SDKs evaluate locally with cached config;
              push updates (SSE) drive freshness
Latency:      <1ms eval (in-process); <2s admin → SDK propagation
Bucketing:    hash(flagKey + userId) mod 10000; subset-preserving
```
