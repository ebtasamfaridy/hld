package com.logger.filter;

import com.logger.core.LogEvent;

public interface Filter {
    enum Result { ALLOW, DENY, NEUTRAL }
    Result decide(LogEvent event);
}
