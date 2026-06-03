package com.carrental.inventory;

import com.carrental.domain.HourBucket;

import java.util.List;

/**
 * Sealed result of a reservation attempt. Either all slots were claimed, or a list
 * of conflicting buckets is returned. No exceptions thrown for the common case.
 */
public sealed interface ReserveResult permits ReserveResult.Reserved, ReserveResult.Conflict {

    record Reserved(List<TimeSlot> slots) implements ReserveResult {}
    record Conflict(List<HourBucket> blocked) implements ReserveResult {}
}
