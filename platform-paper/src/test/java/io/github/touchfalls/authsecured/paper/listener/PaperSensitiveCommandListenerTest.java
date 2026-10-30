package io.github.touchfalls.authsecured.paper.listener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaperSensitiveCommandListenerTest {

    @Test
    void recognizesNamespacedSensitiveCommandWithoutRetainingPrefix() {
        PaperSensitiveCommandListener.ParsedCommand parsed =
                PaperSensitiveCommandListener.parse("/authsecured:login VerySecret");

        assertNotNull(parsed);
        assertEquals("login", parsed.label());
        assertArrayEquals(new String[]{"VerySecret"}, parsed.arguments());
    }

    @Test
    void ignoresMessagesThatAreNotCommands() {
        assertNull(PaperSensitiveCommandListener.parse("login VerySecret"));
    }

    @Test
    void preLoginAllowListAppliesToPasswordCommands() {
        java.util.List<String> allowed = java.util.List.of("/login", "/L", "/register");
        assertTrue(PaperSensitiveCommandListener.isAllowed("login", allowed));
        assertTrue(PaperSensitiveCommandListener.isAllowed("l", allowed));
        assertFalse(PaperSensitiveCommandListener.isAllowed("changepassword", allowed));
        assertFalse(PaperSensitiveCommandListener.isAllowed("cp", allowed));
    }

    @Test
    void declaredPasswordLabelsIncludeAliasesInEitherYamlForm() {
        java.util.Map<String, java.util.Map<String, Object>> commands = java.util.Map.of(
                "login", java.util.Map.of("aliases", java.util.List.of("l", "LOG")),
                "register", java.util.Map.of("aliases", "reg"),
                "changepassword", java.util.Map.of());

        java.util.Map<String, String> labels = PaperSensitiveCommandListener.declaredPasswordLabels(commands);

        assertEquals("login", labels.get("l"));
        assertEquals("login", labels.get("log"));
        assertEquals("register", labels.get("reg"));
        assertEquals("changepassword", labels.get("changepassword"));
        assertNull(labels.get("cp"));
    }

    @Test
    void declaredPasswordLabelsToleratesMissingCommandDefinitions() {
        java.util.Map<String, String> labels = PaperSensitiveCommandListener.declaredPasswordLabels(null);

        assertEquals(java.util.Set.of("login", "register", "changepassword", "2fa"), labels.keySet());
    }
}
