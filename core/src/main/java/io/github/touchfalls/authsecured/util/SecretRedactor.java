package io.github.touchfalls.authsecured.util;

import java.util.Set;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;

public final class SecretRedactor {

    private static final Set<String> DEFAULT_PASSWORD_COMMANDS = Set.of(
            "login", "l", "register", "reg", "changepassword", "cp", "2fa"
    );
    private static volatile Set<String> passwordCommands = DEFAULT_PASSWORD_COMMANDS;
    private static final String REDACTED = "[REDACTED]";

    private SecretRedactor() {}

    /** Publish the aliases actually registered by the hosting command system. */
    public static void registerPasswordAliases(Collection<String> aliases) {
        Set<String> names = new HashSet<>(Set.of("login", "register", "changepassword", "2fa"));
        for (String alias : aliases) {
            if (alias != null && !alias.isBlank()) names.add(unqualified(alias).toLowerCase(Locale.ROOT));
        }
        passwordCommands = Set.copyOf(names);
    }

    public static boolean isPasswordCommand(String label) {
        return label != null && passwordCommands.contains(unqualified(label));
    }

    /**
     * Removes password arguments while keeping enough command context for useful
     * diagnostics. Both slash-prefixed and namespaced command forms are handled.
     */
    public static String redactCommand(String command) {
        if (command == null || command.isBlank()) {
            return command;
        }

        String leadingSlash = command.startsWith("/") ? "/" : "";
        String[] parts = command.substring(leadingSlash.length()).strip().split("\\s+");
        String commandName = unqualified(parts[0]);

        if (isPasswordCommand(commandName)) {
            return redactArguments(leadingSlash, parts, 1);
        }
        if ("authadmin".equals(commandName)
                && parts.length >= 2
                && "resetpassword".equalsIgnoreCase(parts[1])) {
            return redactArguments(leadingSlash, parts, 3);
        }
        return command;
    }

    private static String redactArguments(String leadingSlash, String[] parts, int visibleParts) {
        StringBuilder result = new StringBuilder(leadingSlash);
        int visible = Math.min(visibleParts, parts.length);
        for (int index = 0; index < visible; index++) {
            if (index > 0) {
                result.append(' ');
            }
            result.append(parts[index]);
        }
        for (int index = visible; index < parts.length; index++) {
            result.append(' ').append(REDACTED);
        }
        return result.toString();
    }

    private static String unqualified(String commandName) {
        int namespaceSeparator = commandName.indexOf(':');
        String unqualified = namespaceSeparator >= 0
                ? commandName.substring(namespaceSeparator + 1)
                : commandName;
        return unqualified.toLowerCase(java.util.Locale.ROOT);
    }
}
