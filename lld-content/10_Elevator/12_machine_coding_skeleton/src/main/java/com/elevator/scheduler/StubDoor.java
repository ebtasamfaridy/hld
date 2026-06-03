package com.elevator.scheduler;

public final class StubDoor implements DoorAdapter {
    private boolean open = false;
    @Override public void open()      { this.open = true; }
    @Override public void close()     { this.open = false; }
    @Override public boolean isOpen() { return open; }
}
