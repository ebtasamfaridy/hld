# 21 · E-Commerce (Amazon / Flipkart marketplace style)

> A staff-grade end-to-end LLD of a multi-seller e-commerce marketplace. The interview probes whether you understand **three-layer catalog (Product → SKU → ListingOffer)**, **per-seller inventory atomicity**, **the cart-to-order saga**, **multi-shipment fulfilment**, **two-phase payment**, and the **buybox** that picks one offer when many sellers list the same SKU — at scale.

## What you will master

- The canonical marketplace catalog: a logical **Product** (iPhone 15) → variant **SKU** (iPhone 15 / 256 GB / Black) → many **ListingOffers** (each seller's price + inventory).
- **Atomic per-seller inventory decrement** — the no-oversell rule via PK + CAS, the same mutex pattern as BookMyShow's seat hold and Car Rental's slot grid.
- **Cart with TTL** that softly reserves nothing, and the moment of truth at checkout.
- The **place-order saga** — idempotency lookup, hard-reserve inventory across N items × M sellers, authorize payment, persist Order + Payment + outbox in one TXN.
- **Multi-shipment per order** — one order from three sellers becomes three shipments with three tracking numbers; partial cancellation, partial refund.
- **Two-phase payment** — AUTH at checkout, CAPTURE at ship (per shipment), MIT or refund for variances.
- **Order state machine** with the orthogonal payment + fulfilment dimensions reconciled.
- **Returns and refunds** as a separate aggregate that may move money days after delivery.
- **BuyBox as a Strategy** — pick one offer per SKU based on price, fulfilment SLA, seller rating; explainable.
- **Idempotency at every money-bearing step**, including delayed refunds and seller payouts.
- **Plane separation** — browse plane (eventually consistent, ES + CDN) vs buy plane (strongly consistent, Postgres).

## Read order

| # | File |
| - | --- |
| 0 | [00_simple_language_problem_statement.md](./00_simple_language_problem_statement.md) — **start here** |
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

- **Reserve-then-pay vs pay-then-reserve** — we hard-reserve inventory at "Place Order" with a 15-min TTL during payment authorize. Trades a small inventory-hostage window for "never paid but no stock" UX.
- **Per-seller inventory rows vs aggregate counters** — one `(seller_id, sku_id)` row per offer with optimistic CAS. Cross-seller inventory is *not* aggregated; the buybox layer is the only thing that fans out across offers.
- **Multi-shipment vs single-shipment** — an order spanning sellers becomes N shipments. The order is "complete" only when all shipments deliver; payment captures per shipment.
- **Hard reserve at order vs soft hold in cart** — the cart never reserves anything. The hold happens at checkout, surfacing OUT_OF_STOCK lazily but accurately.
- **Synchronous saga vs orchestrated workflow engine** — V1 is synchronous in-process saga. We graduate to Temporal/Cadence only if the saga grows past 5 steps or needs human-in-the-loop pauses.
- **Browse plane eventual vs strong** — search uses ES with ≤30 s lag. Place-order is the consistency oracle.

## System-specific deep dives

- **Atomic per-offer inventory decrement** — `05_database_design.md` + `11_concurrency_and_scaling.md`
- **Three-layer catalog (Product/SKU/Offer) and BuyBox** — `04_domain_model.md` + `10_design_patterns.md`
- **Place-order saga + multi-shipment split** — `08_sequence_diagrams.md`
- **Two-phase payment with per-shipment capture** — `09_state_machines.md` + `08_sequence_diagrams.md`
- **Returns and refund flow as a separate aggregate** — `09_state_machines.md`
- **Idempotency belt-and-suspenders (our DB + gateway dedup)** — `10_design_patterns.md` + `11_concurrency_and_scaling.md`
- **Cart TTL and oversell at checkout** — `11_concurrency_and_scaling.md`
