package com.elevator;

import com.elevator.domain.HallCall;
import com.elevator.scheduler.Car;
import com.elevator.strategy.DispatchStrategy;

import java.util.List;
import java.util.Optional;

public final class Dispatcher {

    private final DispatchStrategy strategy;

    public Dispatcher(DispatchStrategy strategy) {
        this.strategy = strategy;
    }

    public Optional<Car> assign(HallCall hc, List<Car> cars) {
        Car best = null;
        long bestCost = Long.MAX_VALUE;
        for (Car c : cars) {
            long cost = strategy.cost(c, hc);
            if (cost < bestCost) { bestCost = cost; best = c; }
        }
        return bestCost == Long.MAX_VALUE ? Optional.empty() : Optional.ofNullable(best);
    }
}
