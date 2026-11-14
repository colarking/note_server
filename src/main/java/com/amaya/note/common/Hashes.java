package com.amaya.note.common;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
public final class Hashes {
    private Hashes() {}
    public static String sha256(byte[] input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static String sha256(String input) { return sha256(input.getBytes(StandardCharsets.UTF_8)); }
}
