package com.tictactoe.domain;

import java.util.List;

public record BoardSnapshot(int n, int k, Symbol[][] cells, List<Move> history) {}
