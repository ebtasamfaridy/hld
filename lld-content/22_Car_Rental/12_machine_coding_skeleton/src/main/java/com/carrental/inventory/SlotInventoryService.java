package com.carrental.inventory;

import com.carrental.domain.HourBucket;
import com.carrental.domain.TimeWindow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Atomic time-slot inventory. The map key (vehicleId, bucket) acts as the
 * natural mutex — putIfAbsent mirrors SQL "INSERT ... ON CONFLICT DO NOTHING".
 *
 * The reserve() implementation is all-or-nothing:
 *   - try to claim every bucket in the window
 *   - if any one collides, roll back everything we claimed in this attempt
 */
public final class SlotInventoryService {

    private record Key(UUID vehicleId, HourBucket bucket) {}

    /** Key → reservation_id holding the slot. Absent key means free. */
    private final ConcurrentMap<Key, UUID> store = new ConcurrentHashMap<>();

    public ReserveResult reserve(UUID vehicleId, TimeWindow window, UUID reservationId) {
        List<HourBucket> buckets = window.hourBuckets();
        List<TimeSlot> claimed = new ArrayList<>();
        List<HourBucket> blocked = new ArrayList<>();

        for (HourBucket b : buckets) {
            Key k = new Key(vehicleId, b);
            UUID prev = store.putIfAbsent(k, reservationId);
            if (prev == null || prev.equals(reservationId)) {
                claimed.add(new TimeSlot(vehicleId, b, reservationId));
            } else {
                blocked.add(b);
            }
        }

        if (!blocked.isEmpty()) {
            // Rollback what we just claimed in this call.
            for (TimeSlot s : claimed) {
                store.remove(new Key(s.vehicleId(), s.bucket()), reservationId);
            }
            return new ReserveResult.Conflict(List.copyOf(blocked));
        }
        return new ReserveResult.Reserved(List.copyOf(claimed));
    }

    /** Release every slot held by this reservation. Idempotent. */
    public void release(UUID reservationId) {
        store.entrySet().removeIf(e -> Objects.equals(e.getValue(), reservationId));
    }

    public boolean isWindowFree(UUID vehicleId, TimeWindow window) {
        for (HourBucket b : window.hourBuckets()) {
            if (store.containsKey(new Key(vehicleId, b))) return false;
        }
        return true;
    }

    public Map<HourBucket, UUID> snapshotForVehicle(UUID vehicleId) {
        Map<HourBucket, UUID> out = new java.util.TreeMap<>();
        for (Map.Entry<Key, UUID> e : store.entrySet()) {
            if (e.getKey().vehicleId().equals(vehicleId)) {
                out.put(e.getKey().bucket(), e.getValue());
            }
        }
        return out;
    }
}
