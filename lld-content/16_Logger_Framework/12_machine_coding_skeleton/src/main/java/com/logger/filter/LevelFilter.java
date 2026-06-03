package com.logger.filter;

import com.logger.api.Level;
import com.logger.core.LogEvent;

public final class LevelFilter implements Filter {

    private final Level minimum;
    public LevelFilter(Level minimum) { this.minimum = minimum; }

    @Override public Result decide(LogEvent e) {
        return e.level().rank() >= minimum.rank() ? Result.NEUTRAL : Result.DENY;
    }
}
