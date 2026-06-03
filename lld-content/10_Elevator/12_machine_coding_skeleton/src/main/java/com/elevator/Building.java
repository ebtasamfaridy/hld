package com.elevator;

import com.elevator.domain.BuildingMode;
import com.elevator.domain.Direction;
import com.elevator.domain.HallCall;
import com.elevator.listener.BuildingListener;
import com.elevator.scheduler.Car;

import java.util.List;
import java.util.Optional;

public final class Building {

    private final List<Car> cars;
    private final Dispatcher dispatcher;
    private final BuildingListener listener;
    private final int groundFloor;
    private BuildingMode mode = BuildingMode.NORMAL;

    public Building(List<Car> cars, Dispatcher dispatcher, BuildingListener listener, int groundFloor) {
        this.cars = cars;
        this.dispatcher = dispatcher;
        this.listener = listener;
        this.groundFloor = groundFloor;
    }

    public void hallCall(int floor, Direction dir) {
        if (mode != BuildingMode.NORMAL) return;
        HallCall hc = HallCall.of(floor, dir);
        listener.onHallCallPressed(hc);
        Optional<Car> chosen = dispatcher.assign(hc, cars);
        if (chosen.isEmpty()) return;
        chosen.get().addStop(floor);
        listener.onHallCallAssigned(hc, chosen.get().id());
    }

    public void carCall(int carId, int destination) {
        if (mode != BuildingMode.NORMAL) return;
        cars.stream().filter(c -> c.id() == carId).findFirst().ifPresent(c -> c.addStop(destination));
    }

    public void setMode(BuildingMode newMode) {
        BuildingMode old = this.mode;
        this.mode = newMode;
        listener.onModeChange(old.name(), newMode.name());
        if (newMode == BuildingMode.FIRE || newMode == BuildingMode.EVAC) {
            for (Car c : cars) c.redirectTo(groundFloor);
        }
    }

    public void setCarMaintenance(int carId, boolean enabled) {
        cars.stream().filter(c -> c.id() == carId).findFirst().ifPresent(c -> c.setOutOfService(enabled));
    }

    /** Drive one tick across all cars. */
    public void tick() {
        for (Car c : cars) {
            Car.TickAction action = c.tick();
            listener.onCarTick(c, action);
            // For the simple sim, after arriving we immediately close the door.
            if (action == Car.TickAction.ARRIVED) c.doorsClosed();
        }
    }

    public List<Car> cars() { return List.copyOf(cars); }
    public BuildingMode mode() { return mode; }
}
