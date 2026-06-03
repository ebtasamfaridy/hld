package com.logger.layout;

import com.logger.core.LogEvent;

import java.util.Map;

public final class JsonLayout implements Layout {

    @Override
    public String format(LogEvent e) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        kv(sb, "ts",     e.timestamp().toString());        sb.append(',');
        kv(sb, "level",  e.level().name());                sb.append(',');
        kv(sb, "logger", e.loggerName());                  sb.append(',');
        kv(sb, "thread", e.threadName());                  sb.append(',');
        kv(sb, "msg",    e.renderMessage());

        if (!e.mdc().isEmpty()) {
            sb.append(",\"mdc\":{");
            boolean first = true;
            for (Map.Entry<String, String> en : e.mdc().entrySet()) {
                if (!first) sb.append(',');
                kv(sb, en.getKey(), en.getValue());
                first = false;
            }
            sb.append('}');
        }
        if (e.throwable() != null) {
            sb.append(',');
            kv(sb, "exception", e.throwable().toString());
        }
        sb.append('}').append(System.lineSeparator());
        return sb.toString();
    }

    private static void kv(StringBuilder sb, String k, String v) {
        sb.append('"').append(escape(k)).append("\":\"").append(escape(v)).append('"');
    }
    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
