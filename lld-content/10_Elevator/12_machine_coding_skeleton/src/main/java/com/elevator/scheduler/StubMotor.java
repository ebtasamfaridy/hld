package com.elevator.scheduler;

import com.elevator.domain.Direction;

public final class StubMotor implements MotorAdapter {
    @Override public void moveOneFloor(Direction dir) { /* no-op for sim */ }
    @Override public void stop() { }
}
