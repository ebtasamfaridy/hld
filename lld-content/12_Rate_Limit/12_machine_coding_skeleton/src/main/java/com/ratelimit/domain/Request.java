package com.ratelimit.domain;

public record Request(String ip, String userId, String route, String apiKey) {}
