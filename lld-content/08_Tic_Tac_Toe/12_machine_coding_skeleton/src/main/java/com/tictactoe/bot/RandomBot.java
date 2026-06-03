package com.tictactoe.bot;

import com.tictactoe.domain.BoardSnapshot;
import com.tictactoe.domain.Move;
import com.tictactoe.domain.Symbol;
import com.tictactoe.strategy.PlayerInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class RandomBot implements PlayerInput {
    private final Random rng;
    public RandomBot(Random rng) { this.rng = rng; }

    @Override
    public Move nextMove(BoardSnapshot s, Symbol mySymbol) {
        List<int[]> empties = new ArrayList<>();
        for (int r = 0; r < s.n(); r++)
            for (int c = 0; c < s.n(); c++)
                if (s.cells()[r][c] == null) empties.add(new int[]{r, c});
        int[] pick = empties.get(rng.nextInt(empties.size()));
        return Move.of(pick[0], pick[1], mySymbol);
    }
}
