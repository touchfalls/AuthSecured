package com.example.authsecured.bootstrap;

import com.example.authsecured.paper.command.*;
import com.example.authsecured.paper.listener.*;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class PluginBootstrap {

    private final JavaPlugin plugin;
    private DependencyContainer container;

    public PluginBootstrap(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        this.container = new DependencyContainer(plugin);
        this.container.init();

        registerListeners();
        registerCommands();

        plugin.getLogger().info("AuthSecured plugin successfully enabled!");
    }

    public void disable() {
        if (container != null) {
            container.close();
        }
        plugin.getLogger().info("AuthSecured plugin disabled.");
    }

    private void registerListeners() {
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new PlayerConnectionListener(
                container.getAuthService(),
                container.getRestrictionManager(),
                container.getConfigManager(),
                container.getIpHashService(),
                plugin
        ), plugin);

        pm.registerEvents(new PlayerAuthRestrictionListener(
                container.getRestrictionManager(),
                container.getConfigManager()
        ), plugin);

        pm.registerEvents(new PlayerCommandListener(
                container.getRestrictionManager(),
                container.getConfigManager()
        ), plugin);

        pm.registerEvents(new PlayerInteractionListener(
                container.getRestrictionManager()
        ), plugin);
    }

    private void registerCommands() {
        registerCmd("login", new LoginCommand(container.getAuthService(), container.getRestrictionManager(), container.getConfigManager(), container.getIpHashService(), plugin));
        registerCmd("register", new RegisterCommand(container.getAuthService(), container.getRestrictionManager(), container.getConfigManager(), container.getIpHashService(), plugin));
        registerCmd("changepassword", new ChangePasswordCommand(container.getAuthService(), container.getRestrictionManager(), container.getConfigManager(), container.getIpHashService(), plugin));
        registerCmd("authadmin", new AuthAdminCommand(container.getAuthService(), container.getConfigManager(), plugin));
        registerCmd("logout", new LogoutCommand(container.getAuthService(), container.getRestrictionManager(), container.getConfigManager()));
        registerCmd("authstatus", new AuthStatusCommand(container.getRestrictionManager()));
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
