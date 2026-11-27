package io.github.touchfalls.authsecured.paper.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperSensitiveCommandLogFilterTest {

    @Test
    void blocksEveryPasswordBearingCommandAndAlias() {
        for (String command : new String[]{
                "/login secret", "/l secret", "/register secret secret",
                "/reg secret secret", "/changepassword old new", "/cp old new",
                "/authsecured:login secret", "/AUTHSECURED:REGISTER secret secret"
        }) {
            assertTrue(SensitiveCommandLogMatcher.isSensitiveCommand(command), command);
        }
    }

    @Test
    void allowsUnrelatedCommandsAndMessages() {
        assertFalse(SensitiveCommandLogMatcher.isSensitiveCommand("/help"));
        assertTrue(SensitiveCommandLogMatcher.isSensitiveCommand(" /login secret"));
        assertFalse(SensitiveCommandLogMatcher.isSensitiveCommand("/loginhistory"));
    }

    @Test
    void findsPasswordCommandsLoggedByOtherPlugins() {
        for (String line : new String[]{
                "/login secret", "[Spy] Steve: /login secret", "Steve: /authsecured:register a a",
                "Steve ran \"/cp old new\"", "(/l secret)",
                // Indistinguishable from "/login to": a chat line like this is dropped on purpose.
                "Type /login to continue"
        }) {
            assertTrue(SensitiveCommandLogMatcher.containsCommandWithArgument(line), line);
        }
        for (String line : new String[]{
                "Steve: /login", "Loaded /home/l x", "https://example.com/login x",
                "Steve: /help login secret", null
        }) {
            assertFalse(SensitiveCommandLogMatcher.containsCommandWithArgument(line), String.valueOf(line));
        }
    }
}
