package com.example.authsecured.ports;

public interface PasswordHasher {
    String hash(char[] password);
    boolean verify(char[] password, String hash);
    String getDummyHash();
}
