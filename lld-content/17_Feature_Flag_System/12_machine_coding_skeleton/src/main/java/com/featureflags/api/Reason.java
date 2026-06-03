package com.featureflags.api;

public enum Reason {
    OFF, FALLTHROUGH, RULE_MATCH, PREREQUISITE_FAILED, DEFAULT, FLAG_NOT_FOUND
}
