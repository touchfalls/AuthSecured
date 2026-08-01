package com.example.authsecured.infrastructure.security;

import com.example.authsecured.ports.PasswordHasher;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Objects;

public final class Argon2PasswordHasher implements PasswordHasher {

    private final int memoryKib;
    private final int iterations;
    private final int parallelism;
    private final int hashLength;
    private final int saltLength;
    private final String dummyHash;

    public Argon2PasswordHasher(int memoryKib, int iterations, int parallelism, int hashLength, int saltLength) {
        this.memoryKib = memoryKib;
        this.iterations = iterations;
        this.parallelism = parallelism;
        this.hashLength = hashLength;
        this.saltLength = saltLength;
        this.dummyHash = hash("dummyPassword123".toCharArray());
    }

    public Argon2PasswordHasher() {
        this(65536, 3, 2, 32, 16);
    }

    @Override
    public String hash(char[] password) {
        Objects.requireNonNull(password, "Password cannot be null");
        byte[] salt = SecureRandomProvider.generateSalt(saltLength);
        byte[] hash = generateArgon2idHash(password, salt, memoryKib, iterations, parallelism, hashLength);

        Base64.Encoder encoder = Base64.getEncoder().withoutPadding();
        return String.format("$argon2id$v=19$m=%d,t=%d,p=%d$%s$%s",
                memoryKib, iterations, parallelism,
                encoder.encodeToString(salt),
                encoder.encodeToString(hash));
    }

    @Override
    public boolean verify(char[] password, String encodedHash) {
        if (password == null || encodedHash == null || !encodedHash.startsWith("$argon2id$")) {
            return false;
        }

        try {
            String[] parts = encodedHash.split("\\$");
            if (parts.length != 6) return false;

            // parts[0] is empty
            // parts[1] = argon2id
            // parts[2] = v=19
            // parts[3] = m=65536,t=3,p=2
            // parts[4] = base64 Salt
            // parts[5] = base64 Hash

            String[] params = parts[3].split(",");
            int m = Integer.parseInt(params[0].substring(2));
            int t = Integer.parseInt(params[1].substring(2));
            int p = Integer.parseInt(params[2].substring(2));

            Base64.Decoder decoder = Base64.getDecoder();
            byte[] salt = decoder.decode(parts[4]);
            byte[] expectedHash = decoder.decode(parts[5]);

            byte[] computedHash = generateArgon2idHash(password, salt, m, t, p, expectedHash.length);
            return MessageDigest.isEqual(computedHash, expectedHash);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getDummyHash() {
        return dummyHash;
    }

    private byte[] generateArgon2idHash(char[] password, byte[] salt, int memoryKib, int iterations, int parallelism, int hashLen) {
        byte[] passwordBytes = charToByteArray(password);
        try {
            Argon2Parameters.Builder builder = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withMemoryAsKB(memoryKib)
                    .withIterations(iterations)
                    .withParallelism(parallelism)
                    .withSalt(salt);

            Argon2BytesGenerator generator = new Argon2BytesGenerator();
            generator.init(builder.build());

            byte[] result = new byte[hashLen];
            generator.generateBytes(passwordBytes, result, 0, result.length);
            return result;
        } finally {
            java.util.Arrays.fill(passwordBytes, (byte) 0);
        }
    }

    private byte[] charToByteArray(char[] chars) {
        ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));
        byte[] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);
        return bytes;
    }
}
