package io.github.touchfalls.authsecured.infrastructure.security;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Time-based one-time passwords (RFC 6238: HMAC-SHA1, 30 second steps, 6 digits) and the storage
 * helpers around them. The shared secret is encrypted with AES-GCM before it reaches the database,
 * and backup codes are stored only as keyed hashes.
 */
public final class TotpService {

    public static final int STEP_SECONDS = 30;
    private static final int SECRET_BYTES = 20;
    private static final int BACKUP_CODES = 8;
    private static final int BACKUP_CODE_LENGTH = 10;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    // No 0/O/1/I so a code read off a screen cannot be mistyped.
    private static final String BACKUP_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final SecureRandom random = new SecureRandom();
    private final byte[] encryptionKey;
    private final byte[] backupKey;

    public TotpService(byte[] encryptionKey, byte[] backupKey) {
        this.encryptionKey = encryptionKey.clone();
        this.backupKey = backupKey.clone();
    }

    public byte[] newSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        random.nextBytes(secret);
        return secret;
    }

    public static String toBase32(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        return out.toString();
    }

    public String otpauthUri(String issuer, String accountName, byte[] secret) {
        String encodedIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8).replace("+", "%20");
        String label = encodedIssuer + ":" + URLEncoder.encode(accountName, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + label + "?secret=" + toBase32(secret) + "&issuer=" + encodedIssuer
                + "&algorithm=SHA1&digits=6&period=" + STEP_SECONDS;
    }

    public static long stepAt(long epochMillis) {
        return Math.floorDiv(epochMillis / 1000, STEP_SECONDS);
    }

    /**
     * The time step whose code equals {@code code}, or -1. One step either side of now is accepted
     * for clock drift, and a step at or before {@code lastUsedStep} is refused so a code works once.
     */
    public long matchingStep(byte[] secret, String code, long nowMillis, long lastUsedStep) {
        if (code == null || !code.matches("\\d{6}")) return -1;
        byte[] given = code.getBytes(StandardCharsets.US_ASCII);
        long current = stepAt(nowMillis);
        long match = -1;
        for (long step = current - 1; step <= current + 1; step++) {
            // Every step is compared, so the time taken does not show which one matched.
            boolean equal = MessageDigest.isEqual(given, codeAt(secret, step).getBytes(StandardCharsets.US_ASCII));
            if (equal && step > lastUsedStep) match = Math.max(match, step);
        }
        return match;
    }

    static String codeAt(byte[] secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24) | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8) | (hash[offset + 3] & 0xFF);
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA1 is unavailable", e);
        }
    }

    /** Output is a 12-byte nonce followed by the ciphertext and tag. */
    public byte[] encrypt(byte[] secret) {
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(secret);
            return ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt the authenticator secret", e);
        }
    }

    /** Throws if the blob was tampered with or the server secret changed since it was written. */
    public byte[] decrypt(byte[] blob) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"),
                    new GCMParameterSpec(128, blob, 0, 12));
            return cipher.doFinal(blob, 12, blob.length - 12);
        } catch (GeneralSecurityException | RuntimeException e) {
            throw new IllegalStateException("Could not decrypt the authenticator secret", e);
        }
    }

    /** Codes as shown to the player, for example {@code ABCDE-FGH23}. */
    public List<String> newBackupCodes() {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < BACKUP_CODES; i++) {
            StringBuilder code = new StringBuilder();
            for (int j = 0; j < BACKUP_CODE_LENGTH; j++) {
                if (j == BACKUP_CODE_LENGTH / 2) code.append('-');
                code.append(BACKUP_ALPHABET.charAt(random.nextInt(BACKUP_ALPHABET.length())));
            }
            codes.add(code.toString());
        }
        return codes;
    }

    /** Case and dashes are ignored, so {@code abcde-fgh23} and {@code ABCDEFGH23} are the same code. */
    public String hashBackupCode(String code) {
        String normalized = code == null ? "" : code.replace("-", "").strip().toUpperCase(Locale.ROOT);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(backupKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", e);
        }
    }
}
