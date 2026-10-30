package io.github.touchfalls.authsecured.bootstrap;

import io.github.touchfalls.authsecured.paper.adapter.PaperAuthPlatform;
import io.github.touchfalls.authsecured.paper.adapter.PaperPlayerRestrictionAdapter;
import io.github.touchfalls.authsecured.paper.command.*;
import io.github.touchfalls.authsecured.paper.listener.*;
import io.github.touchfalls.authsecured.paper.logging.PaperSensitiveCommandLogFilter;
import io.github.touchfalls.authsecured.paper.player.PaperMovementLock;
import io.github.touchfalls.authsecured.paper.player.PlayerRestrictionManager;
import io.github.touchfalls.authsecured.paper.scheduler.PaperScheduler;
import io.github.touchfalls.authsecured.util.FailureDiagnostics;
import io.github.touchfalls.authsecured.util.SecretRedactor;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;

public class PluginBootstrap {

    private final JavaPlugin plugin;
    private DependencyContainer container;
    private PlayerRestrictionManager restrictionManager;
    private PaperSensitiveCommandLogFilter sensitiveCommandLogFilter;
    private PaperMovementLock movementLock;
    private final PaperScheduler scheduler;

    public PluginBootstrap(JavaPlugin plugin) {
        this.plugin = plugin;
        this.scheduler = PaperScheduler.create(plugin);
    }

    public void enable() {
        try {
            this.sensitiveCommandLogFilter = PaperSensitiveCommandLogFilter.install();
            this.restrictionManager = new PlayerRestrictionManager();
            PaperPlayerRestrictionAdapter restrictionAdapter = new PaperPlayerRestrictionAdapter(restrictionManager);
            PaperAuthPlatform platform = new PaperAuthPlatform(plugin, restrictionAdapter, scheduler);

            this.container = new DependencyContainer(platform);
            this.container.init();
            this.movementLock = new PaperMovementLock(container.getConfigManager());
            restrictionManager.setMovementLock(movementLock);
            restrictionManager.setCommandListRefresher(playerUuid -> {
                org.bukkit.entity.Player player = Bukkit.getPlayer(playerUuid);
                if (player != null) player.updateCommands();
            });

            registerCommands();
            registerListeners();
            registerAuthenticationTimeoutTask();
            registerMaintenanceTask();
            for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                // Reload with players online: everyone must authenticate again.
                scheduler.runOrSchedule(player, () -> {
                    restrictionManager.setPlayerUnauthenticated(player.getUniqueId(), player.getLocation());
                    player.updateCommands();
                });
            }
        } catch (Throwable failure) {
            // Errors (LinkageError, missing server APIs) must also fail closed: if this method
            // threw, Bukkit would disable the plugin and the server would run without authentication.
            failClosed(failure);
            if (failure instanceof VirtualMachineError fatal) throw fatal;
            return;
        }

        plugin.getLogger().info("AuthSecured plugin successfully enabled!");
    }

    private void failClosed(Throwable failure) {
        try {
            if (container != null) container.close();
        } catch (Throwable closeFailure) {
            plugin.getLogger().warning("AuthSecured cleanup after a failed start also failed ("
                    + FailureDiagnostics.causeTypes(closeFailure) + ").");
        }
        container = null;
        HandlerList.unregisterAll(plugin);
        scheduler.cancelTasks();
        Bukkit.getPluginManager().registerEvents(new InitializationFailureListener(), plugin);
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            scheduler.runOrSchedule(player, () ->
                    player.kickPlayer("AuthSecured failed to initialize. Contact the server administrator."));
        }
        plugin.getLogger().severe("AuthSecured initialization failed (" + FailureDiagnostics.causeTypes(failure)
                + "). New logins are blocked. Sensitive configuration details are hidden.");
    }

    public void disable() {
        try {
            if (movementLock != null) {
                movementLock.unlockAll();
            }
        } finally {
            // The database pool and hash threads must close even if restoring speeds fails.
            if (container != null) {
                container.close();
            }
        }
        if (sensitiveCommandLogFilter != null) {
            sensitiveCommandLogFilter.close();
            sensitiveCommandLogFilter = null;
        }
        plugin.getLogger().info("AuthSecured plugin disabled.");
    }

    private void registerListeners() {
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new PlayerConnectionListener(
                container.getAuthService(),
                restrictionManager,
                container.getConfigManager(),
                container.getIpHashService(),
                container.getAuthenticationTimeoutService(),
                plugin,
                scheduler
        ), plugin);

        pm.registerEvents(new PaperSensitiveCommandListener(plugin, restrictionManager, container.getConfigManager(),
                scheduler), plugin);

        pm.registerEvents(new PlayerAuthRestrictionListener(
                restrictionManager,
                container.getConfigManager()
        ), plugin);

        pm.registerEvents(new PlayerCommandListener(
                restrictionManager,
                container.getConfigManager()
        ), plugin);

        pm.registerEvents(new PlayerInteractionListener(
                restrictionManager
        ), plugin);
    }

    @SuppressWarnings("deprecation") // getPluginMeta() is Paper-only; Spigot needs getDescription().
    private void registerCommands() {
        registerCmd("login", new LoginCommand(container.getCommandExecutor(), container.getConfigManager()));
        registerCmd("register", new RegisterCommand(container.getCommandExecutor(), container.getConfigManager()));
        registerCmd("changepassword", new ChangePasswordCommand(container.getCommandExecutor(), container.getConfigManager()));
        AuthAdminCommand adminCommand = new AuthAdminCommand(container, plugin, scheduler);
        registerCmd("authadmin", adminCommand);
        PluginCommand authAdmin = plugin.getCommand("authadmin");
        if (authAdmin != null) {
            authAdmin.setTabCompleter(adminCommand);
        }
        registerCmd("logout", new LogoutCommand(container.getCommandExecutor(), container.getConfigManager()));
        registerCmd("authstatus", new AuthStatusCommand(container.getCommandExecutor(), container.getConfigManager()));
        registerCmd("sessions", new SessionsCommand(container.getCommandExecutor(), container.getConfigManager()));
        registerCmd("2fa", new TwoFactorCommand(container.getCommandExecutor(), container.getConfigManager()));
        java.util.Map<String, String> declaredLabels = PaperSensitiveCommandListener.declaredPasswordLabels(
                plugin.getDescription().getCommands());
        // Declared aliases stay password commands even when another plugin registered them first
        // (Bukkit then drops them from the active aliases): players still type them with a password.
        java.util.Set<String> passwordAliases = new java.util.HashSet<>(declaredLabels.keySet());
        for (String commandName : java.util.List.of("login", "register", "changepassword", "2fa")) {
            PluginCommand command = plugin.getCommand(commandName);
            if (command != null) passwordAliases.addAll(command.getAliases());
        }
        SecretRedactor.registerPasswordAliases(passwordAliases);
        warnAboutUnavailableAliases(declaredLabels);
    }

    private void warnAboutUnavailableAliases(java.util.Map<String, String> declaredLabels) {
        declaredLabels.forEach((alias, canonical) -> {
            PluginCommand command = plugin.getCommand(canonical);
            if (alias.equals(canonical) || command == null
                    || command.getAliases().stream().anyMatch(alias::equalsIgnoreCase)) {
                return;
            }
            plugin.getLogger().warning("Command alias /" + alias + " is already registered by another plugin. "
                    + "Players who have not logged in are told to use /" + canonical + " instead.");
        });
    }

    private void registerAuthenticationTimeoutTask() {
        scheduler.scheduleGlobalTimer(() -> {
            for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                if (!restrictionManager.isAuthenticated(player.getUniqueId())) {
                    container.getAuthenticationTimeoutService().ensureStarted(player.getUniqueId());
                }
            }
            for (java.util.UUID uuid : container.getAuthenticationTimeoutService().drainExpired(Instant.now())) {
                if (!restrictionManager.isAuthenticated(uuid) && Bukkit.getPlayer(uuid) != null) {
                    container.getSecurityService().logSecurityEvent(
                            null,
                            io.github.touchfalls.authsecured.domain.security.SecurityEventType.AUTH_TIMEOUT,
                            null,
                            "{\"reason\":\"authentication_deadline\"}"
                    );
                    org.bukkit.entity.Player player = Bukkit.getPlayer(uuid);
                    if (player != null) {
                        String reason = container.getConfigManager().getMessage(
                                "messages.authentication-timeout-kick",
                                "&cAuthentication time expired. Reconnect and try again."
                        );
                        scheduler.runOrSchedule(player, () -> player.kickPlayer(reason));
                    }
                }
            }
        }, 20L, 20L);
    }

    private void registerMaintenanceTask() {
        scheduler.scheduleGlobalTimer(
                () -> container.getMaintenanceService().runIfDue(System.currentTimeMillis()),
                20L, 20L);
    }

    private void registerCmd(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand cmd = plugin.getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
        } else {
            plugin.getLogger().warning("Could not register command '/" + name + "': Command missing from plugin.yml");
        }
    }
}
