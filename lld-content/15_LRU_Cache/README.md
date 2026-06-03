# 15 · LRU Cache

> The classic. But the interview isn't whether you remember `HashMap + DoublyLinkedList`. It's whether you can **extend it cleanly to LFU, TTL, write-through, sharded, distributed, and concurrent**, and explain the tradeoffs.

## What you will master

- **Why O(1) get + put requires HashMap + DoublyLinkedList**, and how to walk through the algorithm cleanly under pressure.
- The general **eviction-policy abstraction** — pluggable LRU, LFU, FIFO, MRU, ARC.
- **Concurrency**: striped locks, segmented LRU, lock-free shards.
- **TTL**: lazy vs proactive expiry; per-entry vs cache-wide.
- **Write policies**: write-through, write-back, write-around.
- **Negative caching, stampede protection, single-flight loaders**.
- **Memory bounds**: count-based vs byte-based capacity, Caffeine-style weighers.
- **Distributed cache**: Redis vs in-process; cache invalidation strategies.
- **Cache hierarchy**: L1 in-process + L2 Redis.

## Read order

| # | File |
| - | --- |
| 1 | [01_requirements.md](./01_requirements.md) |
| 2 | [02_capacity_estimation.md](./02_capacity_estimation.md) |
| 3 | [03_hld.md](./03_hld.md) |
| 4 | [04_domain_model.md](./04_domain_model.md) |
| 5 | [05_database_design.md](./05_database_design.md) |
| 6 | [06_api_design.md](./06_api_design.md) |
| 7 | [07_class_diagrams.md](./07_class_diagrams.md) |
| 8 | [08_sequence_diagrams.md](./08_sequence_diagrams.md) |
| 9 | [09_state_machines.md](./09_state_machines.md) |
| 10 | [10_design_patterns.md](./10_design_patterns.md) |
| 11 | [11_concurrency_and_scaling.md](./11_concurrency_and_scaling.md) |
| 12 | [12_machine_coding_skeleton/](./12_machine_coding_skeleton/) |
| 13 | [13_extensions_and_tradeoffs.md](./13_extensions_and_tradeoffs.md) |
| 14 | [14_interviewer_followups.md](./14_interviewer_followups.md) |

## Headline tradeoffs

- **HashMap + DLL** is mandatory for true O(1); LinkedHashMap shortcuts it (production-ready) but skipping the DLL hides understanding.
- **LRU is not always best.** LFU does better for power-law access; ARC adapts; W-TinyLFU (Caffeine) wins benchmarks.
- **TTL eviction is lazy by default**; proactive sweeper for memory pressure.
- **Concurrency**: a single global lock is the wrong default; shard the cache.
- **Distributed cache invalidation** is the hard part — pick *strong* (write-through to Redis) or *eventual* (TTL + version key).
