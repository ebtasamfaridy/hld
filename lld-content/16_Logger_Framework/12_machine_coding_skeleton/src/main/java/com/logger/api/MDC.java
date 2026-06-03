package com.logger.api;

import java.util.HashMap;
import java.util.Map;

public final class MDC {

    private static final ThreadLocal<Map<String, String>> CTX =
            ThreadLocal.withInitial(HashMap::new);

    private MDC() {}

    public static void put(String key, String value) { CTX.get().put(key, value); }
    public static String get(String key)             { return CTX.get().get(key); }
    public static void remove(String key)            { CTX.get().remove(key); }
    public static void clear()                       { CTX.get().clear(); }

    /** Snapshot copy used by LogEvent (immutable view). */
    public static Map<String, String> snapshot() {
        Map<String, String> m = CTX.get();
        return m.isEmpty() ? Map.of() : Map.copyOf(m);
    }

    /** try-with-resources convenience: scoped MDC binding. */
    public static AutoCloseable scoped(String key, String value) {
        String prev = get(key);
        put(key, value);
        return () -> { if (prev == null) remove(key); else put(key, prev); };
    }
}
