# 06 · Elevator System — API Design

## V1 — programmatic

```java
public interface Building {
    void hallCall(int floor, Direction dir);
    void carCall(int carId, int destination);
    void emergencyStop(int carId);
    void setBuildingMode(BuildingMode mode);
    void setCarMaintenance(int carId, boolean maintenance);
    BuildingSnapshot snapshot();
}
```

In a real system, button presses fire `hallCall` / `carCall` directly. The simulator drives the same API.

## V2 — REST + WebSocket (operator console)

```http
GET  /v1/buildings/{id}/state            # snapshot
GET  /v1/buildings/{id}/cars             # per-car details
POST /v1/buildings/{id}/mode             # NORMAL/FIRE/EVAC (operator action)
POST /v1/buildings/{id}/cars/{n}/maintenance  # toggle
GET  /v1/buildings/{id}/audit?from=&to=&kind=
GET  /v1/buildings/{id}/metrics/wait-time?day=
```

WebSocket stream of building events for live dashboards:
```jsonc
{ "kind": "HALL_CALL_PRESSED", "floor": 7, "direction": "UP", "ts": "..." }
{ "kind": "CAR_ARRIVED",       "car_id": 2, "floor": 7, "ts": "..." }
```

## Errors

```jsonc
HTTP/1.1 503
{
  "type": ".../no-cars",
  "title": "All cars out of service"
}
```

## Idempotency

A button press hardware-debounces; we treat repeated `hallCall(floor, UP)` as a no-op if it's already pending. The same applies to `carCall`.

## Output

```
V1:    Building.hallCall / carCall / emergencyStop / setMode / setMaintenance
V2:    REST snapshot + audit, WebSocket live events
Idempotent: repeated button presses absorbed
```
