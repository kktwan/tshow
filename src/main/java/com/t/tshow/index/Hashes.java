package com.t.tshow.index;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/** 색인 변경 판정에 쓰는 해시 (같은 내용이면 항상 같은 값) */
public final class Hashes {

    private Hashes() {
    }

    public static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** payload 의 해시. 키 순서와 상관없이 내용이 같으면 같다 */
    public static String ofPayload(Map<String, Object> payload) {
        StringBuilder sb = new StringBuilder();
        new TreeMap<>(payload).forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
        return sha256(sb.toString());
    }
}
