package com.elevator;

import com.elevator.domain.BuildingMode;
import com.elevator.domain.Capacity;
import com.elevator.domain.Direction;
import com.elevator.listener.ConsoleLogger;
import com.elevator.scheduler.Car;
import com.elevator.scheduler.StubDoor;
import com.elevator.scheduler.StubMotor;
import com.elevator.strategy.LookAheadStrategy;

import java.util.List;

public final class Main {
    public static void main(String[] args) {
        var capacity = new Capacity(8, 600);

        var c1 = new Car(1, 1, 10, 1, capacity, new StubMotor(), new StubDoor());
        var c2 = new Car(2, 1, 10, 5, capacity, new StubMotor(), new StubDoor());
        var c3 = new Car(3, 1, 10, 9, capacity, new StubMotor(), new StubDoor());

        var dispatcher = new Dispatcher(new LookAheadStrategy());
        var building = new Building(List.of(c1, c2, c3), dispatcher, new ConsoleLogger(), 1);

        section("Hall presses");
        building.hallCall(7, Direction.UP);
        building.hallCall(3, Direction.DOWN);
        building.hallCall(8, Direction.UP);

        section("Tick simulation");
        for (int t = 0; t < 12; t++) building.tick();

        section("Car-call inside Car 2 to floor 10");
        building.carCall(2, 10);
        for (int t = 0; t < 8; t++) building.tick();

        section("FIRE mode");
        building.setMode(BuildingMode.FIRE);
        for (int t = 0; t < 12; t++) building.tick();
    }

    private static void section(String s) { System.out.println("\n=== " + s + " ==="); }
}
