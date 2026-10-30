package io.github.touchfalls.authsecured.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TotpServiceTest {

    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private final TotpService totp = new TotpService(new byte[32], new byte[]{1, 2, 3});

    @Test
    void producesTheRfc6238TestVectors() {
        assertEquals("287082", TotpService.codeAt(RFC_SECRET, TotpService.stepAt(59_000L)));
        assertEquals("081804", TotpService.codeAt(RFC_SECRET, TotpService.stepAt(1_111_111_109_000L)));
        assertEquals("005924", TotpService.codeAt(RFC_SECRET, TotpService.stepAt(1_234_567_890_000L)));
        assertEquals("353130", TotpService.codeAt(RFC_SECRET, TotpService.stepAt(20_000_000_000_000L)));
    }

    @Test
    void acceptsOneStepOfDriftButNotMoreAndNeverTwice() {
        long now = 1_111_111_109_000L;
        long step = TotpService.stepAt(now);

        assertEquals(step, totp.matchingStep(RFC_SECRET, "081804", now, 0));
        assertEquals(step, totp.matchingStep(RFC_SECRET, "081804", now + 30_000, 0));
        assertEquals(-1, totp.matchingStep(RFC_SECRET, "081804", now + 90_000, 0));
        assertEquals(-1, totp.matchingStep(RFC_SECRET, "081804", now, step), "a used step is refused");
        assertEquals(-1, totp.matchingStep(RFC_SECRET, "000000", now, 0));
        assertEquals(-1, totp.matchingStep(RFC_SECRET, "81804", now, 0));
        assertEquals(-1, totp.matchingStep(RFC_SECRET, "08180a", now, 0));
        assertEquals(-1, totp.matchingStep(RFC_SECRET, null, now, 0));
    }

    @Test
    void encodesBase32AndBuildsTheAuthenticatorUri() {
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", TotpService.toBase32(RFC_SECRET));
        String uri = totp.otpauthUri("Auth Secured", "Steve", RFC_SECRET);
        assertTrue(uri.startsWith("otpauth://totp/Auth%20Secured:Steve?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"));
        assertTrue(uri.contains("issuer=Auth%20Secured"));
    }

    @Test
    void encryptedSecretsRoundTripAndRejectTampering() {
        byte[] secret = totp.newSecret();
        byte[] blob = totp.encrypt(secret);

        assertEquals(20, secret.length);
        assertArrayEquals(secret, totp.decrypt(blob));
        assertFalse(java.util.Arrays.equals(blob, totp.encrypt(secret)), "each encryption uses a new nonce");
        blob[blob.length - 1] ^= 1;
        assertThrows(IllegalStateException.class, () -> totp.decrypt(blob));
        assertThrows(IllegalStateException.class,
                () -> new TotpService(new byte[]{9}, new byte[0]).decrypt(totp.encrypt(secret)));
    }

    @Test
    void backupCodesAreUniqueAndMatchedIgnoringCaseAndDashes() {
        List<String> codes = totp.newBackupCodes();

        assertEquals(8, codes.size());
        assertEquals(8, codes.stream().distinct().count());
        assertTrue(codes.get(0).matches("[A-HJ-NP-Z2-9]{5}-[A-HJ-NP-Z2-9]{5}"));
        String code = codes.get(0);
        assertEquals(totp.hashBackupCode(code), totp.hashBackupCode(code.toLowerCase().replace("-", "")));
        assertNotEquals(totp.hashBackupCode(code), totp.hashBackupCode(codes.get(1)));
    }
}
