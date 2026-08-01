package com.example.authsecured.fabric.mixin;

import com.example.authsecured.fabric.AuthSecuredFabricMod;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void onPlayerDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        var mod = AuthSecuredFabricMod.getInstance();
        if (mod != null && mod.getRestrictionAdapter() != null) {
            if (!mod.getRestrictionAdapter().isAuthenticated(player.getUuid())) {
                cir.setReturnValue(false);
            }
        }
    }
}
