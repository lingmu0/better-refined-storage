package net.xuwu.bettersophisticatedstorage.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.xuwu.bettersophisticatedstorage.NetworkHandler;
import net.xuwu.bettersophisticatedstorage.common.NetworkStorageMenuAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the same real sidebar slots to every server-side vanilla menu. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin
{
    @Inject(method = "initMenu", at = @At("TAIL"))
    private void bss$addNetworkSlots(AbstractContainerMenu menu, CallbackInfo callbackInfo)
    {
        if (menu == null || bss$isExcludedMenu(menu))
        {
            return;
        }

        NetworkStorageMenuAccess access = (NetworkStorageMenuAccess) menu;
        access.bss$ensureNetworkSlots(0, 0);
        ServerPlayer player = (ServerPlayer) (Object) this;
        access.bss$getNetworkSlots().forEach(slot -> slot.bindPlayer(player));
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean bss$isExcludedMenu(AbstractContainerMenu menu)
    {
        String className = menu.getClass().getName();
        return className.startsWith("com.wintercogs.beyonddimensions.")
                || className.startsWith("org.cyclops.integratedterminals.inventory.container.")
                || className.startsWith("com.refinedmods.refinedstorage.");
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void bss$flushNetworkStorageSync(CallbackInfo callbackInfo)
    {
        NetworkHandler.tick((ServerPlayer) (Object) this);
    }
}

