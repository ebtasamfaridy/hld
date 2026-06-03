package com.featureflags.client;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;

public final class HashBucketing implements Bucketing {

    @Override
    public int bucket(String salt, String userId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest((salt + ":" + userId).getBytes(StandardCharsets.UTF_8));
            // take the first 4 bytes as an unsigned int
            int n = ByteBuffer.wrap(digest, 0, 4).getInt();
            return Math.floorMod(n, 10_000);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
