package com.elevator.listener;

import com.elevator.domain.HallCall;
import com.elevator.scheduler.Car;

public interface BuildingListener {
    void onHallCallPressed(HallCall hc);
    void onHallCallAssigned(HallCall hc, int carId);
    void onCarTick(Car car, Car.TickAction action);
    void onModeChange(String oldMode, String newMode);
}
