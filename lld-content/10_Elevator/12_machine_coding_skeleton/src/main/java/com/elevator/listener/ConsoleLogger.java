package com.elevator.listener;

import com.elevator.domain.HallCall;
import com.elevator.scheduler.Car;

public final class ConsoleLogger implements BuildingListener {
    @Override public void onHallCallPressed(HallCall hc) {
        System.out.printf("  [hall] floor %d %s%n", hc.floor(), hc.direction());
    }
    @Override public void onHallCallAssigned(HallCall hc, int carId) {
        System.out.printf("  [disp] car %d assigned floor %d %s%n", carId, hc.floor(), hc.direction());
    }
    @Override public void onCarTick(Car car, Car.TickAction action) {
        if (action == Car.TickAction.MOVED || action == Car.TickAction.ARRIVED)
            System.out.printf("  [car%d] %s @ floor %d (dir=%s, stops=%s)%n",
                    car.id(), action, car.currentFloor(), car.direction(), car.stops());
    }
    @Override public void onModeChange(String oldMode, String newMode) {
        System.out.printf("  [mode] %s → %s%n", oldMode, newMode);
    }
}
