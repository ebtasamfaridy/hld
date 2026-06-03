package com.logger.layout;

import com.logger.core.LogEvent;

public interface Layout {
    String format(LogEvent event);
}
