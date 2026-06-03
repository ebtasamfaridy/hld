package com.elevator.strategy;

import com.elevator.domain.HallCall;
import com.elevator.scheduler.Car;

public interface DispatchStrategy {
    /** Cost of assigning hallCall to car. Lower is better. Long.MAX_VALUE means cannot accept. */
    long cost(Car car, HallCall hallCall);
}
