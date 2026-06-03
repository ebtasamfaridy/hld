package com.tictactoe.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * O(1) win detection (when k == n) via incremental counters.
 * For k < n we scan windows of size k along the four lines through (r, c) — O(k).
 */
public final class Board {

    private final int n, k;
    private final int symbolCount;
    private final Symbol[][] cells;
    private final int[][] rowCount;        // [row][symbolIdx]
    private final int[][] colCount;
    private final int[]   diagCount;
    private final int[]   antiDiagCount;
    private int filled = 0;
    private final List<Move> history = new ArrayList<>();

    public Board(int n, int k, int symbolCount) {
        if (n < 3) throw new IllegalArgumentException("n>=3");
        if (k < 3 || k > n) throw new IllegalArgumentException("3<=k<=n");
        this.n = n; this.k = k; this.symbolCount = symbolCount;
        this.cells = new Symbol[n][n];
        this.rowCount = new int[n][symbolCount];
        this.colCount = new int[n][symbolCount];
        this.diagCount = new int[symbolCount];
        this.antiDiagCount = new int[symbolCount];
    }

    public boolean place(Move m) {
        int r = m.row(), c = m.col();
        if (!inBounds(r, c)) return false;
        if (cells[r][c] != null) return false;
        Symbol s = m.symbol();
        cells[r][c] = s;
        rowCount[r][s.idx()]++;
        colCount[c][s.idx()]++;
        if (r == c)            diagCount[s.idx()]++;
        if (r + c == n - 1)    antiDiagCount[s.idx()]++;
        filled++;
        history.add(m);
        return true;
    }

    public void undo(int r, int c) {
        Symbol s = cells[r][c];
        if (s == null) return;
        cells[r][c] = null;
        rowCount[r][s.idx()]--;
        colCount[c][s.idx()]--;
        if (r == c)            diagCount[s.idx()]--;
        if (r + c == n - 1)    antiDiagCount[s.idx()]--;
        filled--;
        if (!history.isEmpty()) history.remove(history.size() - 1);
    }

    public boolean wonBy(Symbol s, int r, int c) {
        if (k == n) {
            return rowCount[r][s.idx()] == n
                || colCount[c][s.idx()] == n
                || (r == c            && diagCount[s.idx()]     == n)
                || (r + c == n - 1    && antiDiagCount[s.idx()] == n);
        }
        return scanLine(s, r, c, 0, 1)
            || scanLine(s, r, c, 1, 0)
            || scanLine(s, r, c, 1, 1)
            || scanLine(s, r, c, 1, -1);
    }

    /** Scan the line through (r,c) with direction (dr,dc). True if any window of length k is all s. */
    private boolean scanLine(Symbol s, int r, int c, int dr, int dc) {
        int run = 0;
        // Walk back from (r,c) by k-1 then forward k+ (k-1) cells; count consecutive s's.
        int startR = r - dr * (k - 1);
        int startC = c - dc * (k - 1);
        for (int i = 0; i < 2 * (k - 1) + 1; i++) {
            int rr = startR + dr * i, cc = startC + dc * i;
            if (!inBounds(rr, cc)) { run = 0; continue; }
            if (cells[rr][cc] == s) {
                if (++run >= k) return true;
            } else {
                run = 0;
            }
        }
        return false;
    }

    public boolean isFull() { return filled == n * n; }
    public int n() { return n; }
    public int k() { return k; }
    public Symbol cell(int r, int c) { return cells[r][c]; }
    public List<Move> history() { return Collections.unmodifiableList(history); }

    public BoardSnapshot snapshot() {
        Symbol[][] copy = new Symbol[n][n];
        for (int i = 0; i < n; i++) System.arraycopy(cells[i], 0, copy[i], 0, n);
        return new BoardSnapshot(n, k, copy, List.copyOf(history));
    }

    public Board copy() {
        Board b = new Board(n, k, symbolCount);
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++)
                if (cells[r][c] != null)
                    b.place(new Move(r, c, cells[r][c], java.time.Instant.EPOCH));
        return b;
    }

    private boolean inBounds(int r, int c) { return r >= 0 && r < n && c >= 0 && c < n; }
}
