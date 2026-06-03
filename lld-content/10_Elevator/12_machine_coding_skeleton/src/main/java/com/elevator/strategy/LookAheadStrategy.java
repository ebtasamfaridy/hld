package com.elevator.strategy;

import com.elevator.domain.Direction;
import com.elevator.domain.HallCall;
import com.elevator.scheduler.Car;

/**
 * Estimated time to pickup. Considers:
 *  - if car is already heading toward the floor in the same direction → cheap
 *  - if car must finish current direction first → expensive
 *  - if car has many stops → expensive
 */
public final class LookAheadStrategy implements DispatchStrategy {

    private final long perFloorCost = 10;
    private final long perStopPenalty = 3;
    private final long directionConflictPenalty = 50;

    @Override
    public long cost(Car car, HallCall hc) {
        if (!car.canAccept(hc)) return Long.MAX_VALUE;

        int diff = Math.abs(car.currentFloor() - hc.floor());
        long base = diff * perFloorCost + car.stops().size() * perStopPenalty;

        // If car is moving in the wrong direction, add penalty.
        Direction d = car.direction();
        if (d == Direction.UP && hc.floor() < car.currentFloor()) base += directionConflictPenalty;
        if (d == Direction.DOWN && hc.floor() > car.currentFloor()) base += directionConflictPenalty;
        // Also penalize if car is moving in correct direction but would have to reverse to match hall direction.
        if (d == Direction.UP && hc.direction() == Direction.DOWN) base += directionConflictPenalty / 2;
        if (d == Direction.DOWN && hc.direction() == Direction.UP) base += directionConflictPenalty / 2;

        return base;
    }
}
