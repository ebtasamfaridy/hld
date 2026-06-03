package com.elevator.scheduler;

import com.elevator.domain.Direction;

public interface MotorAdapter {
    void moveOneFloor(Direction dir);
    void stop();
}
