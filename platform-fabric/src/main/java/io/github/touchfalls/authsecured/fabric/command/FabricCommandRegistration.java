package io.github.touchfalls.authsecured.fabric.command;

import io.github.touchfalls.authsecured.bootstrap.AdminOperations;
import io.github.touchfalls.authsecured.domain.auth.TemporaryPasswordReset;
import io.github.touchfalls.authsecured.fabric.AuthSecuredFabricMod;
import io.github.touchfalls.authsecured.ports.UnifiedCommandExecutor;
import io.github.touchfalls.authsecured.util.SecretRedactor;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class FabricCommandRegistration {

    private FabricCommandRegistration() {
    }

    public static void register(AuthSecuredFabricMod mod) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerPlayerCommands(dispatcher, mod);
            registerAdminCommands(dispatcher, mod);
        });
    }

    private static void registerPlayerCommands(CommandDispatcher<CommandSourceStack> dispatcher,
                                               AuthSecuredFabricMod mod) {
        // Brigadier's unquoted StringArgumentType.string() only accepts [0-9A-Za-z_.+-], so a
        // password with any other character would need client-side quoting Paper never requires.
        // greedyString() has no such restriction; splitTokens() then reproduces Paper's own
        // args[0]/args[1] parsing (first token, next token, anything further silently ignored)
        // so a password typed the same way behaves the same way on both platforms.
        dispatcher.register(Commands.literal("register")
                .then(Commands.argument("passwords", StringArgumentType.greedyString())
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            if (!ready(mod, player)) return 0;
                            String[] parts = splitTokens(StringArgumentType.getString(context, "passwords"));
                            if (parts == null) {
                                send(context.getSource(), message(mod, "messages.register-usage",
                                        "§cUsage: /register <password> <confirmPassword>"));
                                return 0;
                            }
                            executor(mod).executeRegister(player.getUUID(), player.getName().getString(),
                                    parts[0].toCharArray(), parts[1].toCharArray(),
                                    mod.getPlayerAddress(player.getUUID()));
                            return 1;
                        })));
        dispatcher.register(Commands.literal("reg").redirect(dispatcher.getRoot().getChild("register")));

        dispatcher.register(Commands.literal("login")
                .then(Commands.argument("password", StringArgumentType.greedyString()).executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    if (!ready(mod, player)) return 0;
                    executor(mod).executeLogin(player.getUUID(), player.getName().getString(),
                            firstToken(StringArgumentType.getString(context, "password")).toCharArray(),
                            mod.getPlayerAddress(player.getUUID()));
                    return 1;
                })));
        dispatcher.register(Commands.literal("l").redirect(dispatcher.getRoot().getChild("login")));

        dispatcher.register(Commands.literal("changepassword")
                .then(Commands.argument("passwords", StringArgumentType.greedyString())
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            if (!ready(mod, player)) return 0;
                            String[] parts = splitTokens(StringArgumentType.getString(context, "passwords"));
                            if (parts == null) {
                                send(context.getSource(), message(mod, "messages.changepass-usage",
                                        "§cUsage: /changepassword <oldPassword> <newPassword>"));
                                return 0;
                            }
                            executor(mod).executeChangePassword(player.getUUID(), player.getName().getString(),
                                    parts[0].toCharArray(), parts[1].toCharArray(),
                                    mod.getPlayerAddress(player.getUUID()));
                            return 1;
                        })));
        dispatcher.register(Commands.literal("cp").redirect(dispatcher.getRoot().getChild("changepassword")));

        dispatcher.register(Commands.literal("logout").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            if (!ready(mod, player)) return 0;
            executor(mod).executeLogout(player.getUUID());
            return 1;
        }));
        dispatcher.register(Commands.literal("authstatus").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            if (!ready(mod, player)) return 0;
            executor(mod).executeAuthStatus(player.getUUID());
            return 1;
        }));
        dispatcher.register(Commands.literal("sessions")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    if (!ready(mod, player)) return 0;
                    executor(mod).executeSessions(player.getUUID(), false);
                    return 1;
                })
                .then(Commands.literal("revoke").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    if (!ready(mod, player)) return 0;
                    executor(mod).executeSessions(player.getUUID(), true);
                    return 1;
                })));
        dispatcher.register(Commands.literal("2fa")
                .executes(context -> twoFactor(mod, context.getSource().getPlayerOrException(), ""))
                .then(Commands.argument("arguments", StringArgumentType.greedyString()).executes(context ->
                        twoFactor(mod, context.getSource().getPlayerOrException(),
                                StringArgumentType.getString(context, "arguments")))));
        Set<String> registeredPasswordNames = new HashSet<>();
        for (String canonical : List.of("login", "register", "changepassword", "2fa")) {
            var node = dispatcher.getRoot().getChild(canonical);
            if (node == null) continue;
            registeredPasswordNames.add(canonical);
            dispatcher.getRoot().getChildren().stream()
                    .filter(candidate -> candidate.getRedirect() == node)
                    .forEach(candidate -> registeredPasswordNames.add(candidate.getName()));
        }
        SecretRedactor.registerPasswordAliases(registeredPasswordNames);
    }

    private static int twoFactor(AuthSecuredFabricMod mod, ServerPlayer player, String arguments) {
        if (!ready(mod, player)) return 0;
        String[] args = arguments.isBlank() ? new String[0] : arguments.strip().split("\\s+");
        executor(mod).executeTwoFactor(player.getUUID(), player.getName().getString(), args,
                mod.getPlayerAddress(player.getUUID()));
        return 1;
    }

    private static void registerAdminCommands(CommandDispatcher<CommandSourceStack> dispatcher,
                                              AuthSecuredFabricMod mod) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("authadmin")
                .requires(source -> mod.isInitialized());

        root.then(Commands.literal("status")
                .requires(source -> permitted(mod, source, "status", 2))
                .executes(context -> completeLines(mod, context.getSource(), operations(mod).status())));
        root.then(Commands.literal("doctor")
                .requires(source -> permitted(mod, source, "doctor", 3))
                .executes(context -> completeLines(mod, context.getSource(), operations(mod).doctor())));
        root.then(Commands.literal("reload")
                .requires(source -> permitted(mod, source, "reload", 3))
                .executes(context -> {
                    boolean reloaded = mod.getContainer().reloadConfig();
                    send(context.getSource(), message(mod,
                            reloaded ? "messages.admin-reloaded" : "messages.admin-reload-failed",
                            reloaded ? "&aConfiguration and localization reloaded."
                                    : "&cReload rejected. The previous valid configuration is still active."));
                    return 1;
                }));

        root.then(Commands.literal("session")
                .requires(source -> permitted(mod, source, "session", 3))
                .then(Commands.literal("gettimeout").executes(context -> {
                    send(context.getSource(), message(mod, "messages.admin-session-current",
                            "&aCurrent session timeout: &e{seconds} seconds.", Map.of(
                                    "seconds", Long.toString(operations(mod).getSessionTimeoutSeconds()))));
                    return 1;
                }))
                .then(Commands.literal("settimeout")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 2_592_000))
                                .executes(context -> {
                                    int seconds = IntegerArgumentType.getInteger(context, "seconds");
                                    operations(mod).setSessionTimeoutSeconds(seconds);
                                    send(context.getSource(), message(mod, "messages.admin-session-changed",
                                            "&aSession timeout changed immediately to &e{seconds} seconds&a. "
                                                    + "Update config.yml to keep it after restart.",
                                            Map.of("seconds", Integer.toString(seconds))));
                                    return 1;
                                }))));

        root.then(Commands.literal("audit")
                .requires(source -> permitted(mod, source, "audit", 3))
                .executes(context -> completeLines(mod, context.getSource(), operations(mod).audit(10, null)))
                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 100))
                        .executes(context -> completeLines(mod, context.getSource(), operations(mod).audit(
                                IntegerArgumentType.getInteger(context, "limit"), null))))
                .then(Commands.literal("player")
                        .then(playerArgument("player")
                                .executes(context -> completeLines(mod, context.getSource(), operations(mod).audit(
                                        10, StringArgumentType.getString(context, "player"))))
                                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 100))
                                        .executes(context -> completeLines(mod, context.getSource(), operations(mod).audit(
                                                IntegerArgumentType.getInteger(context, "limit"),
                                                StringArgumentType.getString(context, "player"))))))));

        root.then(playerBooleanCommand(mod, "unlock", 3, player ->
                mod.getContainer().getAuthService().unlockAccount(player),
                "messages.admin-account-unlocked", "&aAccount unlocked."));
        root.then(playerBooleanCommand(mod, "unregister", 4, player ->
                operations(mod).unregister(player),
                "messages.admin-account-unregistered", "&aAccount unregistered."));

        root.then(Commands.literal("resetpassword")
                .requires(source -> permitted(mod, source, "resetpassword", 4))
                .then(playerArgument("player").executes(context -> {
                    if (!context.getSource().isPlayer()) {
                        // The console and RCON responses are written to the server log;
                        // only an in-game sender can receive the password without logging it.
                        send(context.getSource(), message(mod, "messages.admin-reset-console-blocked",
                                "&cRun this command in-game. The console and RCON cannot receive "
                                        + "the password without writing it to the server log."));
                        return 1;
                    }
                    String player = StringArgumentType.getString(context, "player");
                    operations(mod).issueTemporaryPassword(player).whenComplete((reset, error) ->
                            context.getSource().getServer().execute(() -> deliverTemporaryPassword(
                                    mod, context.getSource(), player, reset, error)));
                    return 1;
                })));

        dispatcher.register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> playerBooleanCommand(
            AuthSecuredFabricMod mod, String name, int defaultLevel,
            Function<String, CompletableFuture<Boolean>> operation,
            String successKey, String successFallback) {
        return Commands.literal(name)
                .requires(source -> permitted(mod, source, name, defaultLevel))
                .then(playerArgument("player").executes(context -> {
                    operation.apply(StringArgumentType.getString(context, "player"))
                            .whenComplete((success, error) -> context.getSource().getServer().execute(() ->
                                    send(context.getSource(), error == null && Boolean.TRUE.equals(success)
                                            ? message(mod, successKey, successFallback)
                                            : message(mod, "messages.admin-account-not-found-or-failed",
                                            "&cAccount not found or operation failed."))));
                    return 1;
                }));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String>
    playerArgument(String name) {
        return Commands.argument(name, StringArgumentType.word()).suggests((context, builder) -> {
            for (String playerName : context.getSource().getServer().getPlayerNames()) {
                if (playerName.toLowerCase(java.util.Locale.ROOT).startsWith(builder.getRemainingLowerCase())) {
                    builder.suggest(playerName);
                }
            }
            return builder.buildFuture();
        });
    }

    private static int completeLines(AuthSecuredFabricMod mod, CommandSourceStack source,
                                     CompletableFuture<List<String>> future) {
        future.whenComplete((lines, error) -> source.getServer().execute(() -> {
            if (error != null) send(source, message(mod, "messages.admin-operation-failed",
                    "&cOperation failed. Check the server log."));
            else lines.forEach(line -> send(source, line));
        }));
        return 1;
    }

    private static void deliverTemporaryPassword(AuthSecuredFabricMod mod, CommandSourceStack source, String player,
                                                 TemporaryPasswordReset reset, Throwable error) {
        if (error != null) {
            send(source, message(mod, "messages.admin-reset-failed",
                    "&cTemporary password creation failed. Check the server log."));
            return;
        }
        if (reset == null) {
            send(source, message(mod, "messages.admin-account-not-found", "&cAccount not found."));
            return;
        }
        char[] password = reset.copyPassword();
        try {
            String passwordText = new String(password);
            String prefix = message(mod, "messages.admin-temporary-password-prefix",
                    "&eTemporary password for &f{player}&e: ", Map.of("player", player));
            sendCopyable(source, prefix, passwordText);
            send(source, message(mod, "messages.admin-temporary-password-expires",
                    "&7Expires at: {expires}. The player must use /changepassword <temporary> <new>.",
                    Map.of("expires", reset.getExpiresAt().toString())));
        } finally {
            Arrays.fill(password, '\0');
            reset.destroy();
        }
    }

    private static boolean permitted(AuthSecuredFabricMod mod, CommandSourceStack source,
                                     String operation, int defaultLevel) {
        int level = mod.getContainer().getConfigManager().getInt(
                "admin.fabric-permission-levels." + operation, defaultLevel, 0, 4);
        return FabricPermissionChecker.hasPermission(source, level);
    }

    private static AdminOperations operations(AuthSecuredFabricMod mod) {
        return new AdminOperations(mod.getContainer());
    }

    private static UnifiedCommandExecutor executor(AuthSecuredFabricMod mod) {
        return mod.getContainer().getCommandExecutor();
    }

    /** The first whitespace-delimited token, matching how Paper's args[0] is produced. */
    static String firstToken(String rest) {
        String trimmed = rest.strip();
        int space = indexOfWhitespace(trimmed);
        return space < 0 ? trimmed : trimmed.substring(0, space);
    }

    /**
     * Splits a greedy capture into exactly two whitespace-delimited tokens, the way Paper's
     * command framework already splits a whole command into {@code args[0]}/{@code args[1]}:
     * anything beyond the second token is silently ignored, just as Paper ignores {@code args[2+]}.
     * Null if there is no second token.
     */
    static String[] splitTokens(String rest) {
        String first = firstToken(rest);
        if (first.isEmpty()) return null;
        String remainder = rest.strip().substring(first.length()).strip();
        String second = firstToken(remainder);
        return second.isEmpty() ? null : new String[]{first, second};
    }

    private static int indexOfWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) return i;
        }
        return -1;
    }

    private static boolean ready(AuthSecuredFabricMod mod, ServerPlayer player) {
        if (mod.isInitialized()) return true;
        player.sendSystemMessage(Component.literal(mod.message("messages.initialization-failed",
                "§cAuthSecured is not initialized.")));
        return false;
    }

    private static void send(CommandSourceStack source, String message) {
        source.sendSystemMessage(Component.literal(message));
    }

    private static void sendCopyable(CommandSourceStack source, String prefix, String password) {
        source.sendSystemMessage(FabricClipboardText.create(prefix, password));
    }

    private static String message(AuthSecuredFabricMod mod, String key, String fallback) {
        return mod.getContainer().getConfigManager().getMessage(key, fallback);
    }

    private static String message(AuthSecuredFabricMod mod, String key, String fallback,
                                  Map<String, String> placeholders) {
        return mod.getContainer().getConfigManager().getMessage(key, fallback, placeholders);
    }

}
