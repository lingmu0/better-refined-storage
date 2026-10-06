package net.xuwu.bettersophisticatedstorage.common;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Consumer;
import java.lang.reflect.Method;

/** Standalone JVM regression; does not start a Minecraft client or server. */
public final class LegacyRefinedStorageRegression
{
    public static void main(String[] args) throws Exception
    {
        SharedConstants.tryDetectVersion();
        // A plain JVM has no ModLauncher transformer to add NetworkEvent's no-arg constructor.
        // Seed the real event bus's parent listener list before the registry bootstrap initializes networking.
        Method listenerList = Class.forName("net.minecraftforge.eventbus.api.EventListenerHelper")
                .getDeclaredMethod("getListenerListInternal", Class.class, boolean.class);
        listenerList.setAccessible(true);
        Class<?> networkEvent = Class.forName("net.minecraftforge.network.NetworkEvent");
        seedListenerList(listenerList, networkEvent);
        Class<?> event = Class.forName("net.minecraftforge.eventbus.api.Event");
        for (Class<?> nested : networkEvent.getDeclaredClasses())
        {
            if (event.isAssignableFrom(nested))
            {
                seedListenerList(listenerList, nested);
            }
        }
        Bootstrap.bootStrap();
        verifyInstalledApiContract();

        FakeNetworkImpl network = new FakeNetworkImpl();
        ItemStack input = new ItemStack(Items.STONE, 8);
        NetworkItemFixture item = new NetworkItemFixture(network);
        check(LegacyRefinedStorageNetwork.resolveNetwork(item, new Object(), input) == network,
                "Bound legacy terminal must resolve without the RS 2 API");
        check(LegacyRefinedStorageNetwork.resolveNetwork(new NetworkItemFixture(null), new Object(), input) == null,
                "Unbound/unloaded legacy terminal must not resolve");

        List<StorageEntry> entries = LegacyRefinedStorageNetwork.readEntries(network);
        check(entries.size() == 1 && entries.get(0).amount() == 128 && entries.get(0).stack().getCount() == 1,
                "Legacy cache counts must become normalized sidebar keys");
        check(network.cache.stack.getCount() == 128, "Snapshot must not alter the RS cache");

        check(LegacyRefinedStorageNetwork.hasPermission(network, null, "MODIFY"), "Allowed grid permission");
        network.security.allowed = false;
        check(!LegacyRefinedStorageNetwork.hasPermission(network, null, "MODIFY"), "Denied grid permission");
        network.security.allowed = true;

        check(LegacyRefinedStorageNetwork.insertIntoNetwork(network, input, true) == 3,
                "Legacy simulated insertion must use the remainder convention");
        check(network.stored == 128 && input.getCount() == 8, "Simulation must preserve network and input");
        check(LegacyRefinedStorageNetwork.insertIntoNetwork(network, input, false) == 3,
                "Legacy insertion must use PERFORM, not RS 2 EXECUTE");
        check(network.stored == 131 && input.getCount() == 8, "Only accepted items change the network");

        ItemStack extracted = LegacyRefinedStorageNetwork.extractFromNetwork(network, input, 5, true);
        check(extracted.getCount() == 5 && network.stored == 131, "Simulated extraction must not consume items");
        extracted = LegacyRefinedStorageNetwork.extractFromNetwork(network, input, 5, false);
        check(extracted.getCount() == 5 && network.stored == 126 && input.getCount() == 8,
                "Legacy extraction must preserve the requested prototype");
        input.getOrCreateTag().putString("fixture", "different");
        check(LegacyRefinedStorageNetwork.extractFromNetwork(network, input, 5, false).isEmpty(),
                "NBT-distinct items must not match");
        check(LegacyRefinedStorageNetwork.extractFromNetwork(network, input, 0, false).isEmpty(),
                "Zero-count extraction must be empty");

        BlockPos origin = BlockPos.ZERO;
        check(LegacyRefinedStorageNetwork.withinRange(3, 4, 0, origin, 6), "In-range transmitter");
        check(!LegacyRefinedStorageNetwork.withinRange(3, 4, 0, origin, 5), "Native strict range boundary");
        check(!LegacyRefinedStorageNetwork.withinRange(0, 0, 0, origin, 0), "Disabled transmitter range");

        System.out.println("Legacy RS regression passed: actual 1.12.4 API contract, bound/unbound network lookup, "
                + "cache snapshots, permissions, simulation, partial insertion, extraction, NBT and range.");
    }

    private static void seedListenerList(Method method, Class<?> event) throws Exception
    {
        Class<?> parent = event.getSuperclass();
        if (parent != null && !parent.getName().equals("net.minecraftforge.eventbus.api.Event"))
        {
            seedListenerList(method, parent);
        }
        method.invoke(null, event, true);
    }

    private static void verifyInstalledApiContract() throws Exception
    {
        ClassLoader loader = LegacyRefinedStorageRegression.class.getClassLoader();
        Class<?> item = Class.forName("com.refinedmods.refinedstorage.item.NetworkItem", false, loader);
        item.getMethod("isValid", ItemStack.class);
        item.getMethod("applyNetwork", MinecraftServer.class, ItemStack.class, Consumer.class, Consumer.class);
        Class<?> network = Class.forName("com.refinedmods.refinedstorage.api.network.INetwork", false, loader);
        Class<?> action = Class.forName("com.refinedmods.refinedstorage.api.util.Action", false, loader);
        network.getMethod("getItemStorageCache");
        network.getMethod("getSecurityManager");
        network.getMethod("canRun");
        network.getMethod("insertItem", ItemStack.class, int.class, action);
        network.getMethod("extractItem", ItemStack.class, int.class, action);
        action.getField("PERFORM");
        action.getField("SIMULATE");
        Class<?> permission = Class.forName("com.refinedmods.refinedstorage.api.network.security.Permission", false, loader);
        permission.getField("MODIFY");
        permission.getField("INSERT");
        permission.getField("EXTRACT");
        Class.forName("com.refinedmods.refinedstorage.api.network.security.ISecurityManager", false, loader)
                .getMethod("hasPermission", permission, Player.class);
        Class.forName("com.refinedmods.refinedstorage.api.storage.cache.IStorageCache", false, loader)
                .getMethod("getList");
        Class.forName("com.refinedmods.refinedstorage.api.util.IStackList", false, loader).getMethod("getStacks");
        Class.forName("com.refinedmods.refinedstorage.api.util.StackListEntry", false, loader).getMethod("getStack");
        try
        {
            Class.forName("com.refinedmods.refinedstorage.common.api.RefinedStorageApi", false, loader);
            throw new AssertionError("Regression fixture must use the legacy RS jar, not RS 2");
        }
        catch (ClassNotFoundException expected)
        {
            // This absent class is precisely why the previous sidebar connection never activated.
        }
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
        {
            throw new AssertionError(message);
        }
    }

    public static final class NetworkItemFixture
    {
        private final Object network;

        NetworkItemFixture(Object network)
        {
            this.network = network;
        }

        public void applyNetwork(Object server, ItemStack terminal, Consumer<Object> success, Consumer<Object> failure)
        {
            if (network == null)
            {
                failure.accept(null);
            }
            else
            {
                success.accept(network);
            }
        }
    }

    // Deliberately package-private implementation, with public API declarations for reflection.
    public interface FakeNetwork
    {
        FakeCache getItemStorageCache();
        FakeSecurity getSecurityManager();
        ItemStack insertItem(ItemStack stack, int count, Object action);
        ItemStack extractItem(ItemStack stack, int count, Object action);
    }

    private static final class FakeNetworkImpl implements FakeNetwork
    {
        final FakeCache cache = new FakeCache();
        final FakeSecurity security = new FakeSecurity();
        int stored = 128;

        public FakeCache getItemStorageCache() { return cache; }
        public FakeSecurity getSecurityManager() { return security; }

        public ItemStack insertItem(ItemStack stack, int count, Object action)
        {
            int accepted = Math.min(3, count);
            if (((Enum<?>) action).name().equals("PERFORM"))
            {
                stored += accepted;
            }
            stack.setCount(count - accepted);
            return stack;
        }

        public ItemStack extractItem(ItemStack stack, int count, Object action)
        {
            if (!ItemStack.isSameItemSameTags(stack, cache.stack))
            {
                return ItemStack.EMPTY;
            }
            int extracted = Math.min(stored, count);
            if (((Enum<?>) action).name().equals("PERFORM"))
            {
                stored -= extracted;
            }
            stack.setCount(extracted);
            return stack;
        }
    }

    public static final class FakeCache
    {
        final ItemStack stack = new ItemStack(Items.STONE, 128);
        public FakeList getList() { return new FakeList(stack); }
    }

    public static final class FakeList
    {
        private final ItemStack stack;
        FakeList(ItemStack stack) { this.stack = stack; }
        public List<FakeEntry> getStacks() { return List.of(new FakeEntry(stack)); }
    }

    public static final class FakeEntry
    {
        private final ItemStack stack;
        FakeEntry(ItemStack stack) { this.stack = stack; }
        public ItemStack getStack() { return stack; }
    }

    public static final class FakeSecurity
    {
        boolean allowed = true;
        public boolean hasPermission(Object permission, Object player) { return allowed; }
    }
}
