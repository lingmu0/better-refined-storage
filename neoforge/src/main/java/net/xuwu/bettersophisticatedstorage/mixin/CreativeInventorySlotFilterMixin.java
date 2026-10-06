package net.xuwu.bettersophisticatedstorage.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.xuwu.bettersophisticatedstorage.common.NetworkStorageSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Prevents CreativeModeInventoryScreen from wrapping sidebar-only slots as inventory slots. */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeInventorySlotFilterMixin
{
    @Unique
    private final List<Slot> bss$temporarilyRemovedNetworkSlots = new ArrayList<>();

    @Inject(method = "selectTab", at = @At("HEAD"))
    private void bss$hideNetworkSlotsWhileBuildingTab(CreativeModeTab tab, CallbackInfo callbackInfo)
    {
        bss$restoreNetworkSlots();
        if (Minecraft.getInstance().player == null)
        {
            return;
        }

        Iterator<Slot> iterator = Minecraft.getInstance().player.inventoryMenu.slots.iterator();
        while (iterator.hasNext())
        {
            Slot slot = iterator.next();
            if (slot instanceof NetworkStorageSlot)
            {
                bss$temporarilyRemovedNetworkSlots.add(slot);
                iterator.remove();
            }
        }
    }

    @Inject(method = "selectTab", at = @At("RETURN"))
    private void bss$restoreNetworkSlotsAfterBuildingTab(CreativeModeTab tab, CallbackInfo callbackInfo)
    {
        bss$restoreNetworkSlots();
    }

    @Unique
    private void bss$restoreNetworkSlots()
    {
        if (bss$temporarilyRemovedNetworkSlots.isEmpty() || Minecraft.getInstance().player == null)
        {
            return;
        }
        Minecraft.getInstance().player.inventoryMenu.slots.addAll(bss$temporarilyRemovedNetworkSlots);
        bss$temporarilyRemovedNetworkSlots.clear();
    }
}

