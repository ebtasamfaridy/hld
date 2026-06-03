package com.elevator.strategy;

import com.elevator.domain.HallCall;
import com.elevator.scheduler.Car;

public final class NearestCarStrategy implements DispatchStrategy {
    @Override
    public long cost(Car car, HallCall hc) {
        if (!car.canAccept(hc)) return Long.MAX_VALUE;
        return Math.abs(car.currentFloor() - hc.floor());
    }
}
