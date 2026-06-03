package com.elevator.scheduler;

import com.elevator.domain.*;

import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeSet;

/**
 * The LOOK scheduler. Moves toward the next stop in the current direction;
 * reverses only when no further stops exist in that direction.
 */
public final class Car {

    private final int id;
    private final int minFloor, maxFloor;
    private final Capacity capacity;
    private final MotorAdapter motor;
    private final DoorAdapter door;

    private int currentFloor;
    private Direction direction = Direction.IDLE;
    private CarStatus status = CarStatus.IDLE;
    private final NavigableSet<Integer> stops = new TreeSet<>();
    private int currentLoad = 0;

    public Car(int id, int minFloor, int maxFloor, int initialFloor,
               Capacity capacity, MotorAdapter motor, DoorAdapter door) {
        this.id = id;
        this.minFloor = minFloor;
        this.maxFloor = maxFloor;
        this.currentFloor = initialFloor;
        this.capacity = capacity;
        this.motor = motor;
        this.door = door;
    }

    public int id() { return id; }
    public int currentFloor() { return currentFloor; }
    public Direction direction() { return direction; }
    public CarStatus status() { return status; }
    public NavigableSet<Integer> stops() { return new TreeSet<>(stops); }
    public int load() { return currentLoad; }
    public Capacity capacity() { return capacity; }

    public boolean isOutOfService()        { return status == CarStatus.OUT_OF_SERVICE; }
    public void setOutOfService(boolean v) { status = v ? CarStatus.OUT_OF_SERVICE : CarStatus.IDLE; }

    /** Add a stop floor. No-op if already present or if out of service. */
    public void addStop(int floor) {
        if (status == CarStatus.OUT_OF_SERVICE) return;
        if (floor < minFloor || floor > maxFloor) return;
        stops.add(floor);
        if (status == CarStatus.IDLE && floor != currentFloor) {
            direction = floor > currentFloor ? Direction.UP : Direction.DOWN;
            status = CarStatus.MOVING;
        }
    }

    /** Clear all stops (e.g., FIRE mode) and head to a single forced floor. */
    public void redirectTo(int floor) {
        stops.clear();
        addStop(floor);
    }

    /** Compute next-stop using LOOK. */
    public Optional<Integer> peekNextStop() {
        if (stops.isEmpty()) return Optional.empty();
        return switch (direction) {
            case UP -> {
                Integer ceil = stops.ceiling(currentFloor);
                if (ceil != null) yield Optional.of(ceil);
                yield Optional.ofNullable(stops.last());   // reverse target
            }
            case DOWN -> {
                Integer floor = stops.floor(currentFloor);
                if (floor != null) yield Optional.of(floor);
                yield Optional.ofNullable(stops.first());
            }
            case IDLE -> {
                Integer hi = stops.ceiling(currentFloor);
                Integer lo = stops.floor(currentFloor);
                if (hi == null) yield Optional.ofNullable(lo);
                if (lo == null) yield Optional.ofNullable(hi);
                yield Optional.of((hi - currentFloor) <= (currentFloor - lo) ? hi : lo);
            }
        };
    }

    /** One simulation tick. Returns the action taken (for logging). */
    public TickAction tick() {
        if (status == CarStatus.OUT_OF_SERVICE) return TickAction.NONE;
        var nextOpt = peekNextStop();
        if (nextOpt.isEmpty()) {
            direction = Direction.IDLE;
            status = CarStatus.IDLE;
            return TickAction.IDLE;
        }
        int next = nextOpt.get();

        if (next == currentFloor) {
            // Arrive at this stop: open door, remove stop, mark direction if needed.
            stops.remove(currentFloor);
            door.open();
            status = CarStatus.DOOR_OPEN;
            return TickAction.ARRIVED;
        }

        // Move one floor toward target.
        if (next > currentFloor) {
            direction = Direction.UP;
            motor.moveOneFloor(Direction.UP);
            currentFloor++;
        } else {
            direction = Direction.DOWN;
            motor.moveOneFloor(Direction.DOWN);
            currentFloor--;
        }
        status = CarStatus.MOVING;
        return TickAction.MOVED;
    }

    /** External signal: doors closed; resume moving. */
    public void doorsClosed() {
        if (status == CarStatus.DOOR_OPEN) {
            door.close();
            status = stops.isEmpty() ? CarStatus.IDLE : CarStatus.MOVING;
            // Reverse direction if no stops in current direction
            if (!stops.isEmpty()) {
                if (direction == Direction.UP && stops.ceiling(currentFloor) == null) direction = Direction.DOWN;
                if (direction == Direction.DOWN && stops.floor(currentFloor) == null) direction = Direction.UP;
            } else {
                direction = Direction.IDLE;
            }
        }
    }

    public boolean canAccept(HallCall hc) {
        if (status == CarStatus.OUT_OF_SERVICE) return false;
        if (currentLoad >= capacity.maxPersons()) return false;
        return true;
    }

    public void addLoad(int delta) { this.currentLoad = Math.max(0, currentLoad + delta); }

    public enum TickAction { NONE, IDLE, MOVED, ARRIVED }
}
