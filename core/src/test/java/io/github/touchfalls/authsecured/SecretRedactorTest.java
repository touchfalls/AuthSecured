package io.github.touchfalls.authsecured;

import io.github.touchfalls.authsecured.util.SecretRedactor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.Set;

class SecretRedactorTest {

    @Test
    void redactsAllPlayerPasswordCommandArguments() {
        assertEquals("login [REDACTED]", SecretRedactor.redactCommand("login hunter2"));
        assertEquals("/register [REDACTED] [REDACTED]",
                SecretRedactor.redactCommand("/register one two"));
        assertEquals("authsecured:changepassword [REDACTED] [REDACTED]",
                SecretRedactor.redactCommand("authsecured:changepassword old new"));
    }

    @Test
    void keepsAdminTargetButRedactsReplacementPassword() {
        assertEquals("/authadmin resetpassword Steve [REDACTED]",
                SecretRedactor.redactCommand("/authadmin resetpassword Steve verySecret"));
    }

    @Test
    void leavesNonSensitiveCommandsUnchanged() {
        assertEquals("authadmin unlock Steve", SecretRedactor.redactCommand("authadmin unlock Steve"));
        assertEquals("help login", SecretRedactor.redactCommand("help login"));
    }

    @Test
    void redactsAliasesPublishedByCommandRegistration() {
        SecretRedactor.registerPasswordAliases(Set.of("signin", "l", "reg", "cp"));
        try {
            assertEquals("/signin [REDACTED]", SecretRedactor.redactCommand("/signin secret"));
        } finally {
            SecretRedactor.registerPasswordAliases(Set.of("l", "reg", "cp"));
        }
    }

    @Test
    void authenticatorCodesAreRedactedFromCommandLogs() {
        org.junit.jupiter.api.Assertions.assertEquals("/2fa [REDACTED]", SecretRedactor.redactCommand("/2fa ABCDE-FGH23"));
        org.junit.jupiter.api.Assertions.assertEquals("/2fa [REDACTED] [REDACTED]", SecretRedactor.redactCommand("/2fa disable 123456"));
    }
}
