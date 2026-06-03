package com.carrental.trip;

import java.util.UUID;

public interface IoTAdapter {
    /** Fire-and-forget unlock. Caller passes an idempotency token; impl dedupes. */
    void unlock(UUID vehicleId, String idemToken);
    void lock(UUID vehicleId, String idemToken);

    /** No-op stub for the demo. */
    static IoTAdapter stub() {
        return new IoTAdapter() {
            @Override public void unlock(UUID vehicleId, String idemToken) {
                System.out.println("  [IoT] UNLOCK " + vehicleId.toString().substring(0,8) + " token=" + idemToken);
            }
            @Override public void lock(UUID vehicleId, String idemToken) {
                System.out.println("  [IoT] LOCK   " + vehicleId.toString().substring(0,8) + " token=" + idemToken);
            }
        };
    }
}
