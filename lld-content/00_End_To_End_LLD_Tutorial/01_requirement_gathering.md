# 01 · Requirement Gathering Framework

> The first 5–10 minutes of any LLD interview decide whether you build the right system or the wrong one. This is the highest-leverage skill.

---

## Why Requirement Gathering Matters

Most LLD candidates jump into entities and classes within 2 minutes. Staff-level candidates spend 5–10 minutes negotiating scope. The interviewer is **secretly evaluating**:

1. Can you separate **what the system does** (functional) from **how well it does it** (non-functional)?
2. Can you spot **ambiguity** before designing?
3. Can you cut scope when needed (not everything fits in 60 minutes)?
4. Do you know the **MVP vs extensions** distinction?

If you skip requirements, you will:
- Design for the wrong scale.
- Miss key entities (e.g., forgetting "rider gets a tip" in food delivery).
- Get blindsided by follow-up questions you haven't considered.

---

## The 4-Layer Requirement Model

Always gather requirements in **4 layers**, in this order:

```
┌─────────────────────────────────────────┐
│ Layer 1: Functional Requirements (FR)   │  WHAT must the system do
├─────────────────────────────────────────┤
│ Layer 2: Non-Functional Requirements    │  HOW WELL (latency, scale, etc.)
├─────────────────────────────────────────┤
│ Layer 3: Out of Scope                   │  Explicitly EXCLUDED
├─────────────────────────────────────────┤
│ Layer 4: Future Extensions              │  V2 / V3 hints — show foresight
└─────────────────────────────────────────┘
```

### Layer 1: Functional Requirements

These describe **observable behavior**. Always phrased as user actions or system responsibilities.

**Good FR (verb-driven, specific, testable):**
- "A user can place an order containing 1+ items from a single restaurant."
- "A driver can accept or reject a delivery request within 15 seconds."
- "An order can be cancelled until it transitions to PREPARING."

**Bad FR (vague, untestable):**
- "The system should be fast." (← this is NFR)
- "Users can use the app." (← what does that mean?)
- "There should be a database." (← that's HLD/implementation)

### Layer 2: Non-Functional Requirements (NFR)

NFRs are quality attributes. Memorize this checklist:

| NFR | Sample question | Why it matters |
| --- | --- | --- |
| **Latency** | "p99 < 200ms for placing an order" | Drives sync vs async, cache strategy |
| **Throughput / QPS** | "1M orders/day, peak 200 RPS" | Drives sharding, horizontal scaling |
| **Availability** | "99.95% — 4.5 hr/year downtime" | Drives replication, multi-AZ |
| **Consistency** | "Strong for payments, eventual for feed" | Drives DB choice, txn boundaries |
| **Durability** | "No order loss after ack" | Drives WAL, sync replication |
| **Scalability** | "Handle 10x in 1 year" | Drives stateless services, partitioning |
| **Security** | "PII encrypted at rest" | Drives encryption, RBAC |
| **Cost** | "Optimize storage cost" | Drives TTL, archival |
| **Maintainability** | "Add new payment method in <1 day" | Drives Strategy pattern, plugin design |

### Layer 3: Out of Scope

Explicitly call these out. It shows discipline and lets you focus.

> "I'm assuming we don't need to design the payment gateway internals — we'll integrate via PaymentProviderClient. We're also not designing the mobile app, just the backend."

### Layer 4: Future Extensions

Mention 2–3 things the system **could** do later. This signals you're thinking long-term.

> "V2 could add multi-restaurant orders, scheduled deliveries, and a subscription model. The design should not block these."

---

## Clarification Questions Interviewers Expect

For **every** LLD problem, ask at least these 5 categories:

### 1. Scope & Actors

- Who are the users? (customer, driver, admin, restaurant, support agent)
- Are we building B2C or B2B? Single-tenant or multi-tenant?
- Web, mobile, or both? Does that matter for our LLD?

### 2. Core Functionality

- What is the **core happy path**? Walk through it user-by-user.
- What are the **3–5 must-have features**?
- What are the **3 features I should explicitly skip**?

### 3. Scale

- How many active users? Daily? Concurrent?
- Read-to-write ratio?
- Peak vs average traffic?
- Data retention — keep forever or TTL?

### 4. Data & Consistency

- Is strong consistency required for any flow? (payments? inventory?)
- Is eventual consistency acceptable elsewhere? (search index, feed)
- Multi-region or single-region?

### 5. Integration & Constraints

- External systems we depend on? (payment, SMS, maps, push)
- SLAs we must meet?
- Tech stack constraints? (must be JVM, must use Postgres, etc.)

---

## Scope Negotiation: How to Cut Without Sounding Lazy

If the problem is too big for 60 minutes, **negotiate visibly**:

> "Designing the full Swiggy stack would take days. For this hour, I'll deeply design **order placement, dispatch, and tracking** — the core revenue path. I'll mention extensions like ratings, promotions, and analytics, but won't dive in unless you'd prefer."

This signals:
- You understand the problem is huge.
- You know what's important (revenue path > admin tooling).
- You're willing to dive deeper if asked.

**Never** silently drop features. Always say what you're skipping and why.

---

## Core vs Extension Features — The MVP Filter

Use this filter:

```
For each feature, ask:
  - Does the system make MONEY without it?       (Core)
  - Does the system FUNCTION without it?         (Core if no, Extension if yes)
  - Is it a NICE-TO-HAVE polish?                 (Extension)
```

### Example: Food Delivery

| Feature | Core / Extension | Why |
| --- | --- | --- |
| Place order | Core | Revenue depends on it |
| Restaurant menu | Core | Can't order without it |
| Driver assignment | Core | Order can't be delivered |
| Live tracking | Core (modern UX) | DAU collapses without it |
| Reviews/Ratings | Extension | System runs fine for V1 |
| Promo codes | Extension | Can launch later |
| Multi-restaurant cart | Extension | Major complexity, low MVP value |
| Subscription (Swiggy One) | Extension | Independent feature |

State this matrix out loud. Interviewers love it.

---

## Worked Example: Requirements for "Design Splitwise"

Here is what a good first 5 minutes look like:

> **You:** Before designing, I'd like to clarify scope.
>
> **Functional**
> - Users can create groups and add members.
> - Users can record an expense paid by one and split among many.
> - Splits can be: equal, by exact amount, by percentage, by share.
> - Users can view their balance with each other user (net debt).
> - Users can settle a debt (record a payment).
> - The system can simplify debts in a group (minimize transactions).
>
> **Non-Functional**
> - Strong consistency for balances — no money should "disappear".
> - p99 < 300ms for read APIs (balance, expenses).
> - Scale: 100M users, 10M DAU, 1M expenses/day.
> - Audit log for every balance mutation.
>
> **Out of Scope**
> - Actual payment processing (we'll only record settlements).
> - Currency conversion FX rates (assume single currency for V1, mention multi-currency as extension).
> - Friend graph / social feed.
>
> **Extensions**
> - Multi-currency.
> - Recurring expenses.
> - Receipt OCR.

This sets up a clean, scoped 60-minute design.

---

## Anti-Patterns (Things That Will Lose Points)

| Anti-pattern | Why it hurts |
| --- | --- |
| Jumping to entities in <2 min | Skipping the negotiation step |
| Treating "scalable" as a feature | NFR, not FR |
| "I'll just add caching later" | Caching is an architectural choice, not a fix |
| Forgetting the admin / support actor | Real systems always have ops surface |
| No mention of failure modes | Systems fail; design must address it |
| "Use microservices" without scope | Premature decomposition |

---

## Output of This Step

By the end of the requirements phase, you should be able to write down (mentally or on the whiteboard):

```
Actors:           [Customer, Driver, Restaurant, Admin]
Core Use Cases:   [Place order, Assign driver, Track order, Cancel order]
NFR:              [p99 < 200ms, 200 RPS peak, Strong consistency for payments]
Out of Scope:     [Payment internals, Restaurant onboarding, Analytics]
Extensions:       [Multi-restaurant, Scheduled, Subscriptions]
```

Every subsequent step (entities, APIs, DB) traces back to this.

---

## Checklist

- [ ] Asked who the actors are.
- [ ] Listed 5–8 functional requirements as testable user actions.
- [ ] Stated 4–6 non-functional requirements with **numbers**.
- [ ] Explicitly listed 2–4 things out of scope.
- [ ] Mentioned 2–3 future extensions.
- [ ] Confirmed all of this with the interviewer before moving on.
