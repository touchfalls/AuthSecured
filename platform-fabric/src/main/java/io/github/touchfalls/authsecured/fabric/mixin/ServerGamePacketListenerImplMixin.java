package io.github.touchfalls.authsecured.fabric.mixin;

import io.github.touchfalls.authsecured.fabric.AuthSecuredFabricMod;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Shadow
    public ServerPlayer player;

    @Shadow
    private Vec3 awaitingPositionFromClient;

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void authsecured$blockMovement(ServerboundMovePlayerPacket packet, CallbackInfo callback) {
        if (!restricted("restrictions.freeze-player", true)) {
            return;
        }
        callback.cancel();
        onServerThread(() -> {
            if (player.isPassenger()) {
                player.stopRiding();
            }
            // The client predicts its own movement, so ignoring the packet alone would let the
            // player walk around locally. Pull it back to the server position immediately.
            if (packet.hasPosition() && awaitingPositionFromClient == null
                    && player.distanceToSqr(packet.getX(player.getX()), packet.getY(player.getY()),
                    packet.getZ(player.getZ())) > 1.0E-6) {
                ((ServerGamePacketListenerImpl) (Object) this).teleport(player.getX(), player.getY(), player.getZ(),
                        packet.getYRot(player.getYRot()), packet.getXRot(player.getXRot()));
            }
        });
    }

    @Inject(method = "handleMoveVehicle", at = @At("HEAD"), cancellable = true)
    private void authsecured$blockVehicleMovement(ServerboundMoveVehiclePacket packet, CallbackInfo callback) {
        if (restricted("restrictions.freeze-player", true)) {
            callback.cancel();
            onServerThread(player::stopRiding);
        }
    }

    @Inject(method = "handleTeleportToEntityPacket", at = @At("HEAD"), cancellable = true)
    private void authsecured$blockSpectatorTeleport(ServerboundTeleportToEntityPacket packet, CallbackInfo callback) {
        if (restricted("restrictions.freeze-player", true)) {
            callback.cancel();
        }
    }

    @Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"), cancellable = true)
    private void authsecured$blockCreativeSlot(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo callback) {
        if (restricted("restrictions.block-inventory", true)) {
            callback.cancel();
        }
    }

    @Inject(method = "handlePlayerAbilities", at = @At("HEAD"), cancellable = true)
    private void authsecured$blockFlight(ServerboundPlayerAbilitiesPacket packet, CallbackInfo callback) {
        AuthSecuredFabricMod mod = AuthSecuredFabricMod.getInstance();
        if (mod != null && mod.shouldBlockFlight(player.getUUID())) {
            callback.cancel();
            onServerThread(() -> player.getAbilities().flying = false);
        }
    }

    @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
    private void authsecured$blockInventoryClick(ServerboundContainerClickPacket packet, CallbackInfo callback) {
        if (restricted("restrictions.block-inventory", true)) {
            callback.cancel();
        }
    }

    @Inject(method = "handleContainerButtonClick", at = @At("HEAD"), cancellable = true)
    private void authsecured$blockInventoryButton(ServerboundContainerButtonClickPacket packet, CallbackInfo callback) {
        if (restricted("restrictions.block-inventory", true)) {
            callback.cancel();
        }
    }

    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void authsecured$blockUseItemOn(ServerboundUseItemOnPacket packet, CallbackInfo callback) {
        if (restricted("restrictions.block-interactions", true)
                || restricted("restrictions.block-block-place", true)) {
            callback.cancel();
        }
    }

    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void authsecured$blockUseItem(ServerboundUseItemPacket packet, CallbackInfo callback) {
        if (restricted("restrictions.block-interactions", true)) {
            callback.cancel();
        }
    }

    @Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
    private void authsecured$blockEntityInteraction(ServerboundInteractPacket packet, CallbackInfo callback) {
        if (restricted("restrictions.block-interactions", true)
                || restricted("restrictions.block-damage-dealt", true)) {
            callback.cancel();
        }
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void authsecured$blockPlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo callback) {
        String action = packet.getAction().name();
        boolean dropAction = action.startsWith("DROP_");
        boolean destroyAction = action.contains("DESTROY_BLOCK");
        boolean swapAction = action.startsWith("SWAP_ITEM");
        if ((dropAction && restricted("restrictions.block-item-drop", true))
                || (destroyAction && restricted("restrictions.block-block-break", true))
                || (swapAction && restricted("restrictions.block-interactions", true))) {
            callback.cancel();
        }
    }

    // The handlers below share one callback each; none of them needs its packet. "require" is the
    // number of handlers listed: the default (1) would be satisfied by any one of them, and a
    // handler renamed by a future version would silently stop being covered.

    /**
     * Operator tools that run commands or rewrite the world without the command packet. On an
     * offline-mode server a connection under an operator's name could use them before logging in.
     */
    @Inject(method = {"handleSetCommandBlock", "handleSetCommandMinecart", "handleSetStructureBlock",
            "handleSetJigsawBlock", "handleJigsawGenerate", "handleChangeDifficulty"},
            at = @At("HEAD"), cancellable = true, require = 6)
    private void authsecured$blockOperatorTools(CallbackInfo callback) {
        if (restricted("restrictions.block-commands", true)) {
            callback.cancel();
        }
    }

    @Inject(method = {"handleRenameItem", "handleSelectTrade", "handleSetBeaconPacket", "handlePlaceRecipe"},
            at = @At("HEAD"), cancellable = true, require = 4)
    private void authsecured$blockContainerActions(CallbackInfo callback) {
        if (restricted("restrictions.block-inventory", true)) {
            callback.cancel();
        }
    }

    @Inject(method = {"handleEditBook", "handleSignUpdate"}, at = @At("HEAD"), cancellable = true, require = 2)
    private void authsecured$blockTextEdits(CallbackInfo callback) {
        if (restricted("restrictions.block-interactions", true)) {
            callback.cancel();
        }
    }

    /** Sprinting, leaving a bed, starting to glide, horse jumps. */
    @Inject(method = "handlePlayerCommand", at = @At("HEAD"), cancellable = true)
    private void authsecured$blockPlayerCommand(CallbackInfo callback) {
        if (restricted("restrictions.freeze-player", true)) {
            callback.cancel();
        }
    }

    // require stays at the default (1): if a future version renames these handlers the
    // mod must fail to load instead of silently letting pre-login commands through.
    @Inject(method = "handleChatCommand", at = @At("HEAD"), cancellable = true)
    private void authsecured$blockCommand(ServerboundChatCommandPacket packet, CallbackInfo callback) {
        blockRestrictedCommand(packet.command(), callback);
    }

    /** Signed commands (/msg, /me, or any command from a modified client) use a separate packet. */
    @Inject(method = "handleSignedChatCommand", at = @At("HEAD"), cancellable = true)
    private void authsecured$blockSignedCommand(ServerboundChatCommandSignedPacket packet, CallbackInfo callback) {
        blockRestrictedCommand(packet.command(), callback);
    }

    private void blockRestrictedCommand(String command, CallbackInfo callback) {
        AuthSecuredFabricMod mod = AuthSecuredFabricMod.getInstance();
        if (mod != null && mod.isCommandBlocked(player.getUUID(), command)) {
            callback.cancel();
            player.sendSystemMessage(Component.literal(mod.message("messages.command-blocked",
                    "§cYou must authenticate before using this command!")));
        }
    }

    /**
     * These injections run at the head of the packet handlers, which the network thread enters
     * first. Dropping the packet there is safe; changing the player or the world is not.
     */
    private void onServerThread(Runnable change) {
        AuthSecuredFabricMod mod = AuthSecuredFabricMod.getInstance();
        if (mod != null) {
            mod.runOnServerThread(() -> {
                // The player may have logged in while this waited for the server thread.
                if (!mod.getRestrictionAdapter().isAuthenticated(player.getUUID())) change.run();
            });
        }
    }

    private boolean restricted(String path, boolean defaultValue) {
        AuthSecuredFabricMod mod = AuthSecuredFabricMod.getInstance();
        return mod != null && mod.isRestricted(player.getUUID(), path, defaultValue);
    }
}
