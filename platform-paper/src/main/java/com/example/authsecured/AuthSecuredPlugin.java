package com.example.authsecured;

import com.example.authsecured.bootstrap.PluginBootstrap;
import org.bukkit.plugin.java.JavaPlugin;

public class AuthSecuredPlugin extends JavaPlugin {

    private PluginBootstrap bootstrap;

    @Override
    public void onEnable() {
        this.bootstrap = new PluginBootstrap(this);
        this.bootstrap.enable();
    }

    @Override
    public void onDisable() {
        if (this.bootstrap != null) {
            this.bootstrap.disable();
        }
    }
}
