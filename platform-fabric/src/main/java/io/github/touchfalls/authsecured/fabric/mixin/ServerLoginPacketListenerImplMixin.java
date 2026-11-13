package io.github.touchfalls.authsecured.fabric.mixin;

import com.mojang.authlib.GameProfile;
import io.github.touchfalls.authsecured.fabric.AuthSecuredFabricMod;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginPacketListenerImplMixin {

    @Shadow @Final
    private MinecraftServer server;

    @Shadow @Final
    private Connection connection;

    @Shadow
    private String requestedUsername;

    @Shadow
    public abstract void disconnect(Component reason);

    /**
     * This method is where vanilla kicks the online player with the same profile to let the new
     * connection in. In offline mode anyone can join under another player's name, so the new
     * connection is refused first when it would replace a logged-in player from another address.
     */
    @Inject(method = "verifyLoginAndFinishConnectionSetup", at = @At("HEAD"), cancellable = true)
    private void authsecured$denyForeignReplacement(GameProfile profile, CallbackInfo callback) {
        AuthSecuredFabricMod mod = AuthSecuredFabricMod.getInstance();
        if (mod != null && mod.isForeignReplacement(server, requestedUsername, connection.getRemoteAddress())) {
            callback.cancel();
            disconnect(Component.literal(mod.message("messages.duplicate-login-denied",
                    "§cThis player is already online.")));
        }
    }
}
