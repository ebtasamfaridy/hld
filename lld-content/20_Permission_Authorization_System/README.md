# 20 · Permission / Authorization System (RBAC + ABAC + ReBAC)

> "Can user U perform action A on resource R?" That's the only question this system answers. The art is making it expressive (model the real world), correct (no false ALLOWs), fast (sub-ms reads), and auditable.

## What you will master

- **RBAC**: Roles + Permissions assigned to Roles + Users have Roles. The 80% answer for most apps.
- **Hierarchical roles**: `admin > editor > viewer`; permission inheritance.
- **ABAC**: Policy expressions over attributes (user, resource, environment).
- **ReBAC** (relationship-based): "user is the owner of document X" via graph traversal — Google Zanzibar / SpiceDB style.
- **Policy decision point (PDP)** vs **policy enforcement point (PEP)** — separation of concerns.
- **Fast reads**: caching effective permissions; check operations < 1 ms.
- **Multi-tenancy**: scoping permissions per tenant.
- **Resource hierarchies**: folders containing files; permissions inherit.
- **Negative permissions / DENY rules** and their pitfalls.
- **Audit log** for every check and every grant change.
- **Effective permission queries**: "what can user U do?" and "who can do A on R?" (the inverse).

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

- **RBAC is enough** for most apps; jumping to ABAC/ReBAC adds complexity.
- **Materialized permissions** (cache effective set per user) gives sub-ms reads.
- **DENY > ALLOW** by convention to make rules safer (deny wins).
- **Resource hierarchy** is powerful but expensive — pre-compute or cache traversals.
- **PEP keeps the check; PDP holds the policy** — clean boundary makes systems testable.
