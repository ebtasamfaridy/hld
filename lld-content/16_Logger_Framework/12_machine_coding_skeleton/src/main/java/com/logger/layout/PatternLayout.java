package com.logger.layout;

import com.logger.core.LogEvent;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.format.DateTimeFormatter;

/**
 * Tokens supported (subset of Log4j):
 *   %d   timestamp ISO-8601
 *   %p   level
 *   %t   thread
 *   %c   logger name
 *   %m   rendered message
 *   %X   MDC map
 *   %n   newline
 */
public final class PatternLayout implements Layout {

    private final String pattern;
    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_INSTANT;

    public PatternLayout()              { this("%d %-5p [%t] %c - %m %X%n"); }
    public PatternLayout(String pattern) { this.pattern = pattern; }

    @Override
    public String format(LogEvent e) {
        StringBuilder sb = new StringBuilder(128);
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c != '%' || i + 1 >= pattern.length()) { sb.append(c); continue; }

            // optional minus + width: "%-5p"
            int j = i + 1;
            int width = 0; boolean leftAlign = false;
            if (pattern.charAt(j) == '-') { leftAlign = true; j++; }
            while (j < pattern.length() && Character.isDigit(pattern.charAt(j))) {
                width = width * 10 + (pattern.charAt(j) - '0'); j++;
            }
            if (j >= pattern.length()) { sb.append(c); continue; }
            char tok = pattern.charAt(j);
            String s;
            switch (tok) {
                case 'd' -> s = TS.format(e.timestamp());
                case 'p' -> s = e.level().name();
                case 't' -> s = e.threadName();
                case 'c' -> s = e.loggerName();
                case 'm' -> s = e.renderMessage();
                case 'X' -> s = e.mdc().isEmpty() ? "" : e.mdc().toString();
                case 'n' -> s = System.lineSeparator();
                default  -> s = "%" + tok;
            }
            if (width > 0) {
                if (s.length() < width) {
                    int pad = width - s.length();
                    if (leftAlign) sb.append(s).append(" ".repeat(pad));
                    else           sb.append(" ".repeat(pad)).append(s);
                } else sb.append(s);
            } else sb.append(s);
            i = j;
        }
        if (e.throwable() != null) {
            sb.append(System.lineSeparator());
            StringWriter sw = new StringWriter();
            e.throwable().printStackTrace(new PrintWriter(sw));
            sb.append(sw);
        }
        return sb.toString();
    }
}
