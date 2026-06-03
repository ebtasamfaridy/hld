package com.scheduler.api;

@FunctionalInterface
public interface Task {
    void execute(TaskContext ctx) throws Exception;
}
