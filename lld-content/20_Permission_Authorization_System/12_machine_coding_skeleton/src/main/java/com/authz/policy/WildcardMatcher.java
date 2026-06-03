package com.authz.policy;

public final class WildcardMatcher {

    private WildcardMatcher() {}

    /**
     * Match patterns:
     *   "*"       matches anything
     *   "doc:42"  literal
     *   "doc:*"   any single-segment under doc
     *   "folder:5/*" any path beneath folder:5
     */
    public static boolean match(String pattern, String value) {
        if (pattern.equals("*")) return true;
        if (pattern.equals(value)) return true;

        // suffix wildcard
        if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 1); // keep trailing slash? remove */
            String stem = pattern.substring(0, pattern.length() - 2);
            return value.startsWith(stem + "/") || value.equals(stem);
        }
        if (pattern.endsWith(":*")) {
            String stem = pattern.substring(0, pattern.length() - 2);
            return value.startsWith(stem + ":");
        }
        return false;
    }
}
