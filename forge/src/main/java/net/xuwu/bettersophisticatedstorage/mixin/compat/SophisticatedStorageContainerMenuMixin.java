package net.xuwu.bettersophisticatedstorage.mixin.compat;

import net.minecraft.core.NonNullList;
import net.xuwu.bettersophisticatedstorage.common.NetworkStorageSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/** Keeps Sophisticated Core's slot-integrity guard from counting our appended real slots. */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase", remap = false)
public abstract class SophisticatedStorageContainerMenuMixin
{
    @Redirect(
            method = "hasSomethingMessedWithStorage",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/NonNullList;size()I", remap = false),
            remap = false,
            require = 0
    )
    private int bss$countNativeSlots(NonNullList<?> slots)
    {
        return bss$countWithoutNetworkSlots(slots);
    }

    /** Covers releases compiled against the List interface instead of NonNullList. */
    @Redirect(
            method = "hasSomethingMessedWithStorage",
            at = @At(value = "INVOKE", target = "Ljava/util/List;size()I", remap = false),
            remap = false,
            require = 0
    )
    private int bss$countNativeListSlots(List<?> slots)
    {
        return bss$countWithoutNetworkSlots(slots);
    }

    @Unique
    private static int bss$countWithoutNetworkSlots(Iterable<?> slots)
    {
        int count = 0;
        for (Object slot : slots)
        {
            if (!(slot instanceof NetworkStorageSlot))
            {
                count++;
            }
        }
        return count;
    }
}

