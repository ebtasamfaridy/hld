package com.snakeladder.domain;

public sealed interface Jumper permits Snake, Ladder {
    int from();
    int to();
    JumperKind kind();
}
