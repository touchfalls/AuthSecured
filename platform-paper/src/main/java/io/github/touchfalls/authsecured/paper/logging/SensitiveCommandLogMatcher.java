package io.github.touchfalls.authsecured.paper.logging;

import java.util.Locale;
import io.github.touchfalls.authsecured.util.SecretRedactor;

final class SensitiveCommandLogMatcher {

    private SensitiveCommandLogMatcher() {
    }

    static boolean isSensitiveCommand(String rawCommand) {
        if (rawCommand == null) return false;
        String command = rawCommand.stripLeading();
        if (command.startsWith("/")) command = command.substring(1);
        int end = 0;
        while (end < command.length() && !Character.isWhitespace(command.charAt(end))) end++;
        if (end == 0) return false;
        String label = command.substring(0, end).toLowerCase(Locale.ROOT);
        int namespace = label.lastIndexOf(':');
        if (namespace >= 0) label = label.substring(namespace + 1);
        return SecretRedactor.isPasswordCommand(label);
    }

    /**
     * A password command with an argument anywhere in a log line, such as {@code "[Spy] Steve: /login x"}:
     * other plugins log commands in their own formats. The slash must start a word, so a path like
     * {@code /home/l x} does not match the {@code l} alias.
     */
    static boolean containsCommandWithArgument(String line) {
        if (line == null) return false;
        for (int slash = line.indexOf('/'); slash >= 0; slash = line.indexOf('/', slash + 1)) {
            if (slash > 0 && !Character.isWhitespace(line.charAt(slash - 1))
                    && "\"'([<{".indexOf(line.charAt(slash - 1)) < 0) continue;
            int end = slash + 1;
            while (end < line.length() && !Character.isWhitespace(line.charAt(end))) end++;
            if (!isSensitiveCommand(line.substring(slash, end))) continue;
            if (!line.substring(end).isBlank()) return true;
        }
        return false;
    }
}
