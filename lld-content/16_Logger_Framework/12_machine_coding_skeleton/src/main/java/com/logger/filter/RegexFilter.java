package com.logger.filter;

import com.logger.core.LogEvent;

import java.util.regex.Pattern;

public final class RegexFilter implements Filter {

    private final Pattern pattern;
    private final boolean denyOnMatch;

    public RegexFilter(String regex, boolean denyOnMatch) {
        this.pattern = Pattern.compile(regex);
        this.denyOnMatch = denyOnMatch;
    }

    @Override public Result decide(LogEvent e) {
        boolean matches = pattern.matcher(e.renderMessage()).find();
        if (matches) return denyOnMatch ? Result.DENY : Result.ALLOW;
        return Result.NEUTRAL;
    }
}
