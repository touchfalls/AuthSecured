package com.example.authsecured.fabric.command;

import com.example.authsecured.ports.UnifiedCommandExecutor;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;

public class FabricCommandRegistration {

    public static void registerCommands(UnifiedCommandExecutor commandExecutor) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            // /register <password> <confirmPassword>
            var registerNode = CommandManager.literal("register")
                    .then(CommandManager.argument("password", StringArgumentType.string())
                            .then(CommandManager.argument("confirmPassword", StringArgumentType.string())
                                    .executes(context -> {
                                        ServerPlayerEntity player = context.getSource().getPlayer();
                                        if (player == null) return 0;
                                        String pass = StringArgumentType.getString(context, "password");
                                        String confirm = StringArgumentType.getString(context, "confirmPassword");
                                        String ip = player.getIp();
                                        commandExecutor.executeRegister(player.getUuid(), player.getName().getString(), pass.toCharArray(), confirm.toCharArray(), ip);
                                        return 1;
                                    })
                            )
                    );
            dispatcher.register(registerNode);
            dispatcher.register(CommandManager.literal("reg").redirect(dispatcher.getRoot().getChild("register")));

            // /login <password>
            var loginNode = CommandManager.literal("login")
                    .then(CommandManager.argument("password", StringArgumentType.string())
                            .executes(context -> {
                                ServerPlayerEntity player = context.getSource().getPlayer();
                                if (player == null) return 0;
                                String pass = StringArgumentType.getString(context, "password");
                                String ip = player.getIp();
                                commandExecutor.executeLogin(player.getUuid(), player.getName().getString(), pass.toCharArray(), ip);
                                return 1;
                            })
                    );
            dispatcher.register(loginNode);
            dispatcher.register(CommandManager.literal("l").redirect(dispatcher.getRoot().getChild("login")));

            // /changepassword <oldPassword> <newPassword>
            var changePassNode = CommandManager.literal("changepassword")
                    .then(CommandManager.argument("oldPassword", StringArgumentType.string())
                            .then(CommandManager.argument("newPassword", StringArgumentType.string())
                                    .executes(context -> {
                                        ServerPlayerEntity player = context.getSource().getPlayer();
                                        if (player == null) return 0;
                                        String oldPass = StringArgumentType.getString(context, "oldPassword");
                                        String newPass = StringArgumentType.getString(context, "newPassword");
                                        String ip = player.getIp();
                                        commandExecutor.executeChangePassword(player.getUuid(), oldPass.toCharArray(), newPass.toCharArray(), ip);
                                        return 1;
                                    })
                            )
                    );
            dispatcher.register(changePassNode);
            dispatcher.register(CommandManager.literal("cp").redirect(dispatcher.getRoot().getChild("changepassword")));

            // /logout
            dispatcher.register(CommandManager.literal("logout")
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        if (player == null) return 0;
                        commandExecutor.executeLogout(player.getUuid());
                        return 1;
                    })
            );

            // /authstatus
            dispatcher.register(CommandManager.literal("authstatus")
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        if (player == null) return 0;
                        commandExecutor.executeAuthStatus(player.getUuid());
                        return 1;
                    })
            );
        });
    }
}
