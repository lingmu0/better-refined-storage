package net.xuwu.bettersophisticatedstorage.common;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Exercises copied network/view keys in a standalone JVM, without a client or server. */
public final class StorageMatchingRegression
{
    public static void main(String[] arguments) throws Exception
    {
        SharedConstants.tryDetectVersion();
        // NeoForge's vanilla-registry bootstrap reads the discovered-mod list for feature flags.
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        Bootstrap.bootStrap();

        ItemStack networkStack = new ItemStack(Items.STONE, 64);
        ItemStack viewKey = networkStack.copyWithCount(1);
        require(!networkStack.equals(viewKey), "The test must use distinct ItemStack instances");

        Method findEntry = StorageActions.class.getDeclaredMethod("findEntry", List.class, ItemStack.class);
        findEntry.setAccessible(true);
        StorageEntry stored = new StorageEntry(networkStack, 128);
        List<StorageEntry> entries = List.of(stored);
        require(findEntry.invoke(null, entries, viewKey) == stored,
                "A copied view key must match the server resource regardless of stack count");
        require(findEntry.invoke(null, entries, new ItemStack(Items.DIRT)) == null,
                "Different items must not match");

        ItemStack named = viewKey.copy();
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Named stone"));
        require(findEntry.invoke(null, entries, named) == null,
                "Different data components must not match");
        StorageEntry namedEntry = new StorageEntry(named, 5);
        require(findEntry.invoke(null, List.of(namedEntry), named.copyWithCount(32)) == namedEntry,
                "Copied items with matching data components must match");
        require(findEntry.invoke(null, entries, ItemStack.EMPTY) == null,
                "An empty view cell must not resolve to a network resource");

        AbstractContainerMenu menu = new AbstractContainerMenu(null, 1)
        {
            @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        };
        NetworkStorageSlot slot = new NetworkStorageSlot(menu, 0, 0, 0);
        StorageEntry resolved = (StorageEntry) findEntry.invoke(null, entries, viewKey);
        slot.update(0, resolved.stack(), resolved.amount(), true);
        require(slot.hasItem() && slot.getStoredAmount() == 128,
                "Matching the client's key must keep the server sidebar slot available for pickup");

        Method merge = PortableStorageNetwork.Connection.class.getDeclaredMethod(
                "mergeEntry", List.class, ItemStack.class, long.class);
        merge.setAccessible(true);
        List<StorageEntry> merged = new ArrayList<>();
        merge.invoke(null, merged, networkStack, 40L);
        merge.invoke(null, merged, viewKey, 2L);
        merge.invoke(null, merged, named, 5L);
        require(merged.size() == 2 && merged.get(0).amount() == 42 && merged.get(1).amount() == 5,
                "Equivalent stacks must aggregate while distinct components stay separate");
        require(networkStack.getCount() == 64 && viewKey.getCount() == 1,
                "Matching must not mutate caller-owned stacks");

        System.out.println("Storage matching regression passed: copied keys, counts, components, "
                + "server-slot availability, resource aggregation, and input preservation.");
    }

    private static void require(boolean condition, String message)
    {
        if (!condition)
        {
            throw new AssertionError(message);
        }
    }
}
