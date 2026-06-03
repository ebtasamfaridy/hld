# 13 · Parking Lot — Extensions & Tradeoffs

## Extensions

### 1. Reservations
HOLD + CONFIRM flow. DB exclusion constraint to prevent overlap. Released on no-show after grace.

### 2. Subscriptions / monthly passes
A subscription holds a guaranteed-spot promise during a window. Allocation strategy reserves these spots first.

### 3. License-plate recognition (LPR)
Camera reads plate at gate; ticketless entry. Reservation lookup by plate. Manual fallback if read fails.

### 4. Surge pricing
`SurgePricingStrategy` decorator: multiplies base rate when occupancy > 90 %.

### 5. EV charging billing
Separate from parking fee. EV spots track `kWh_consumed`; bill kWh × ratePerKwh in addition to time fee.

### 6. Valet parking
Driver hands keys; valet parks. The parking system models the valet as a "virtual driver" with permission to park any vehicle. Adds `parker_id` to ticket.

### 7. Multi-lot operator dashboard
Aggregate occupancy / revenue across lots. Materialized views.

### 8. Sensor-based misuse detection
If sensor at spot X reports vehicle but no ACTIVE ticket assigned to it, alert. Or if vehicle parks in EV spot but isn't EV, alert and surcharge.

### 9. Loss-of-power graceful degradation
Gates fall back to "manual mode" — barrier permanently up; record entries on paper; reconcile when power returns.

## Tradeoffs

### CAS in-memory vs `SELECT … FOR UPDATE` in DB

| Criterion | In-memory CAS | SELECT FOR UPDATE |
| --- | --- | --- |
| Latency | ~ns | ~ms |
| Cross-process safety | no | yes |
| Crash safety | no | yes |
| Decision | **CAS for V1**; DB UPDATE for production. Same model. |

### Greedy strategy iteration order vs preference scoring

| Criterion | Iterate-and-take | Score-then-pick |
| --- | --- | --- |
| Correctness | first match | best match |
| Performance | early exit | scan all |
| Use case | simple FIFO | EV preference |
| Decision | scoring + iterate; small lots can scan; large lots use indexed lookup. |

### Compatibility as function vs class hierarchy

| Criterion | Function | Hierarchy |
| --- | --- | --- |
| Add new VehicleType | edit one switch | add class + visit relations |
| Add new SpotType | same | combinatorial |
| Visibility | one place | scattered |
| Decision | **Function** ✓ |

### Pricing as Decorator vs flat strategies

`FreeFirstWindow(Tiered(...))` is more reusable than a single `FreeFirstWindowTiered`. Decorator wins when the orthogonal axes (free window, tier, surge) compose.

## Open questions

- Should we charge for time spent finding a spot (driving to it)? (Convention: timer starts at gate ticketing.)
- Reservation no-show penalty: charge full vs partial? (Policy.)
- LPR confidence threshold for ticketless entry. (Tuning.)

## Output

```
Extensions:    reservations, subscriptions, LPR, surge, EV billing, valet, multi-lot, sensors
Pre-decided:   CAS for V1, function for compatibility, decorator for pricing,
               function-based allocation iteration with preference scoring
Open Qs:       drive-time billing, no-show policy, LPR thresholds
```
