package com.example.authsecured.infrastructure.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class IpHashService {

    private final byte[] secretKey;

    public IpHashService(String secret) {
        if (secret == null || secret.isBlank()) {
            this.secretKey = "DefaultAuthSecuredIpSecretKey123!".getBytes(StandardCharsets.UTF_8);
        } else {
            this.secretKey = secret.getBytes(StandardCharsets.UTF_8);
        }
    }

    public byte[] hashIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            ipAddress = "0.0.0.0";
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secretKey, "HmacSHA256");
            mac.init(keySpec);
            return mac.doFinal(ipAddress.trim().getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256 for IP address", e);
        }
    }
}
