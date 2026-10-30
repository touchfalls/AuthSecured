package io.github.touchfalls.authsecured.infrastructure.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.touchfalls.authsecured.ports.PlatformLogger;

public class IpHashService {

    private final byte[] secretKey;
    private final PlatformLogger logger;
    private final AtomicBoolean missingAddressReported = new AtomicBoolean();

    public IpHashService(String secret) {
        this(secret, null);
    }

    public IpHashService(String secret, PlatformLogger logger) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("A private IP hashing secret is required");
        }
        this.secretKey = secret.getBytes(StandardCharsets.UTF_8);
        this.logger = logger;
    }

    public byte[] hashIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            if (logger != null && missingAddressReported.compareAndSet(false, true)) {
                logger.severe("Player IP address is unavailable. Check proxy forwarding; IP-bound sessions are disabled for affected players.");
            }
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secretKey, "HmacSHA256");
            mac.init(keySpec);
            return mac.doFinal(normalizeForHashing(ipAddress.trim()).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256 for IP address", e);
        }
    }

    /**
     * IPv4 addresses hash unchanged. IPv6 addresses are rounded to their /64
     * network prefix before hashing: a /64 is the smallest block most ISPs
     * assign to one customer, so per-address IP rate limits and account/IP
     * registration caps would otherwise be trivially bypassed by rotating the
     * low 64 bits. Session IP-binding becomes per-/64 for IPv6 too, which also
     * tolerates the address rotation (SLAAC privacy addresses) common on IPv6.
     */
    private static String normalizeForHashing(String ipAddress) {
        try {
            InetAddress address = InetAddress.getByName(ipAddress);
            if (!(address instanceof Inet6Address)) {
                // IPv4, including IPv4-mapped IPv6 literals (the JDK unwraps those); canonical
                // form matches what socket addresses already give, so this changes nothing in practice.
                return address.getHostAddress();
            }
            byte[] prefix = new byte[16];
            System.arraycopy(address.getAddress(), 0, prefix, 0, 8);
            return InetAddress.getByAddress(prefix).getHostAddress();
        } catch (UnknownHostException e) {
            return ipAddress; // not a literal IP address; hash it as given
        }
    }

    /** A 32-byte key for another purpose, derived from the server secret so it needs no second secret file. */
    public byte[] deriveKey(String purpose) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
            return mac.doFinal(("authsecured:key:" + purpose).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to derive a key", e);
        }
    }

    public String hashIpToString(String ipAddress) {
        byte[] hash = hashIp(ipAddress);
        if (hash == null) return null;
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
