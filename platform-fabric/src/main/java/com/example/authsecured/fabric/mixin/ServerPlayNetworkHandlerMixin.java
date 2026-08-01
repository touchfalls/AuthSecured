package com.example.authsecured.fabric.mixin;

import com.example.authsecured.fabric.AuthSecuredFabricMod;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {

    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "onPlayerMove", at = @At("HEAD"), cancellable = true)
    private void onPlayerMove(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        var mod = AuthSecuredFabricMod.getInstance();
        if (mod != null && mod.getRestrictionAdapter() != null) {
            if (!mod.getRestrictionAdapter().isAuthenticated(player.getUuid())) {
                ci.cancel();
            }
        }
    }
}
