package net.xuwu.bettersophisticatedstorage.common;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static net.xuwu.bettersophisticatedstorage.common.PortableStorageNetwork.invokeCompatible;
import static net.xuwu.bettersophisticatedstorage.common.PortableStorageNetwork.invokeStatic;
import static net.xuwu.bettersophisticatedstorage.common.PortableStorageNetwork.reportRefinedStorageApiFailure;

/** RS 1.12.x bridge for Forge 1.20.1; deliberately independent of the RS 2.x API. */
final class LegacyRefinedStorageNetwork
{
    private static final String NETWORK_ITEM_CLASS = "com.refinedmods.refinedstorage.item.NetworkItem";
    private static final String WIRELESS_TRANSMITTER_CLASS =
            "com.refinedmods.refinedstorage.api.network.IWirelessTransmitter";
    private static final String PERMISSION_CLASS =
            "com.refinedmods.refinedstorage.api.network.security.Permission";
    private static final String ACTION_CLASS = "com.refinedmods.refinedstorage.api.util.Action";

    private final ServerPlayer player;
    private final ItemStack terminal;
    private final Object network;
    private final Object wirelessConfig;

    private LegacyRefinedStorageNetwork(ServerPlayer player, ItemStack terminal, Object network,
                                       Object wirelessConfig)
    {
        this.player = player;
        this.terminal = terminal;
        this.network = network;
        this.wirelessConfig = wirelessConfig;
    }

    static PortableStorageNetwork.Connection find(ServerPlayer player)
    {
        for (int index = 0; index < player.getInventory().getContainerSize(); index++)
        {
            PortableStorageNetwork.Connection connection = connect(player, player.getInventory().getItem(index));
            if (connection != null)
            {
                return connection;
            }
        }
        ItemStack terminal = PortableStorageNetwork.findCuriosStack(player,
                PortableStorageNetwork::isRefinedStorageWirelessGrid);
        return connect(player, terminal);
    }

    private static PortableStorageNetwork.Connection connect(ServerPlayer player, ItemStack terminal)
    {
        if (!PortableStorageNetwork.isRefinedStorageWirelessGrid(terminal))
        {
            return null;
        }
        try
        {
            Class<?> itemClass = Class.forName(NETWORK_ITEM_CLASS);
            if (!itemClass.isInstance(terminal.getItem())
                    || !Boolean.TRUE.equals(invokeStatic(itemClass, "isValid", terminal)))
            {
                return null;
            }
            // applyNetwork validates the binding and loaded controller without opening a grid menu.
            Object network = resolveNetwork(terminal.getItem(), player.getServer(), terminal);
            if (network == null)
            {
                return null;
            }
            Object serverConfig = Class.forName("com.refinedmods.refinedstorage.RS")
                    .getField("SERVER_CONFIG").get(null);
            Object wirelessConfig = invokeCompatible(serverConfig, "getWirelessGrid");
            if (wirelessConfig == null)
            {
                return null;
            }
            LegacyRefinedStorageNetwork bridge =
                    new LegacyRefinedStorageNetwork(player, terminal, network, wirelessConfig);
            return bridge.available() ? new PortableStorageNetwork.Connection(bridge) : null;
        }
        catch (Throwable exception)
        {
            reportRefinedStorageApiFailure(exception);
            return null;
        }
    }

    static Object resolveNetwork(Object networkItem, Object server, ItemStack terminal)
            throws ReflectiveOperationException
    {
        Object[] resolved = new Object[1];
        Consumer<Object> success = value -> resolved[0] = value;
        Consumer<Object> failure = ignored -> { };
        invokeCompatible(networkItem, "applyNetwork", server, terminal, success, failure);
        return resolved[0];
    }

    private boolean available() throws ReflectiveOperationException
    {
        // Match the native wireless grid's controller, range, security and energy checks.
        if (!Boolean.TRUE.equals(invokeCompatible(network, "canRun"))
                || !hasPermission(network, player, "MODIFY") || !inWirelessRange())
        {
            return false;
        }
        IEnergyStorage energy = terminal.getCapability(ForgeCapabilities.ENERGY).orElse(null);
        return !usesEnergy() || energy == null || energy.getEnergyStored() > usage("getOpenUsage");
    }

    private boolean inWirelessRange() throws ReflectiveOperationException
    {
        Object graph = invokeCompatible(network, "getNodeGraph");
        Object nodes = invokeCompatible(graph, "all");
        if (!(nodes instanceof Iterable<?> iterable))
        {
            return false;
        }
        Class<?> transmitterClass = Class.forName(WIRELESS_TRANSMITTER_CLASS);
        for (Object entry : iterable)
        {
            Object node = invokeCompatible(entry, "getNode");
            if (!transmitterClass.isInstance(node)
                    || !Boolean.TRUE.equals(invokeCompatible(node, "isActive"))
                    || !player.level().dimension().equals(invokeCompatible(node, "getDimension")))
            {
                continue;
            }
            Object originValue = invokeCompatible(node, "getOrigin");
            Object rangeValue = invokeCompatible(node, "getRange");
            if (originValue instanceof BlockPos origin && rangeValue instanceof Number range
                    && withinRange(player.getX(), player.getY(), player.getZ(), origin, range.doubleValue()))
            {
                return true;
            }
        }
        return false;
    }

    static boolean withinRange(double x, double y, double z, BlockPos origin, double range)
    {
        double dx = x - origin.getX();
        double dy = y - origin.getY();
        double dz = z - origin.getZ();
        return range > 0 && dx * dx + dy * dy + dz * dz < range * range;
    }

    private boolean usesEnergy() throws ReflectiveOperationException
    {
        Object type = invokeCompatible(terminal.getItem(), "getType");
        return Boolean.TRUE.equals(invokeCompatible(wirelessConfig, "getUseEnergy"))
                && !(type instanceof Enum<?> value && value.name().equals("CREATIVE"));
    }

    private int usage(String method) throws ReflectiveOperationException
    {
        Object value = invokeCompatible(wirelessConfig, method);
        return value instanceof Number number ? Math.max(0, number.intValue()) : 0;
    }

    private void drainEnergy(String method)
    {
        // A post-transfer energy failure must never hide a transfer that already succeeded.
        try
        {
            if (usesEnergy())
            {
                IEnergyStorage energy = terminal.getCapability(ForgeCapabilities.ENERGY).orElse(null);
                if (energy != null)
                {
                    energy.extractEnergy(usage(method), false);
                }
            }
        }
        catch (Throwable exception)
        {
            reportRefinedStorageApiFailure(exception);
        }
    }

    List<StorageEntry> entries()
    {
        try
        {
            return available() ? readEntries(network) : List.of();
        }
        catch (Throwable exception)
        {
            reportRefinedStorageApiFailure(exception);
            return List.of();
        }
    }

    int insert(ItemStack input, boolean simulate)
    {
        try
        {
            if (!available() || !hasPermission(network, player, "INSERT"))
            {
                return 0;
            }
            int inserted = insertIntoNetwork(network, input, simulate);
            if (inserted > 0 && !simulate)
            {
                drainEnergy("getInsertUsage");
            }
            return inserted;
        }
        catch (Throwable exception)
        {
            reportRefinedStorageApiFailure(exception);
            return 0;
        }
    }

    ItemStack extract(ItemStack prototype, long maxAmount, boolean simulate)
    {
        try
        {
            if (!available() || !hasPermission(network, player, "EXTRACT"))
            {
                return ItemStack.EMPTY;
            }
            ItemStack extracted = extractFromNetwork(network, prototype, maxAmount, simulate);
            if (!extracted.isEmpty() && !simulate)
            {
                drainEnergy("getExtractUsage");
            }
            return extracted;
        }
        catch (Throwable exception)
        {
            reportRefinedStorageApiFailure(exception);
            return ItemStack.EMPTY;
        }
    }

    static boolean hasPermission(Object network, Object player, String permissionName)
            throws ReflectiveOperationException
    {
        Object permission = Class.forName(PERMISSION_CLASS).getField(permissionName).get(null);
        Object security = invokeCompatible(network, "getSecurityManager");
        return Boolean.TRUE.equals(invokeCompatible(security, "hasPermission", permission, player));
    }

    static List<StorageEntry> readEntries(Object network) throws ReflectiveOperationException
    {
        Object cache = invokeCompatible(network, "getItemStorageCache");
        Object list = invokeCompatible(cache, "getList");
        Object stacks = invokeCompatible(list, "getStacks");
        List<StorageEntry> result = new ArrayList<>();
        if (stacks instanceof Iterable<?> iterable)
        {
            for (Object entry : iterable)
            {
                Object value = invokeCompatible(entry, "getStack");
                if (value instanceof ItemStack stack && !stack.isEmpty() && stack.getCount() > 0)
                {
                    ItemStack key = stack.copy();
                    key.setCount(1);
                    result.add(new StorageEntry(key, stack.getCount()));
                }
            }
        }
        return result;
    }

    private static Object action(boolean simulate) throws ReflectiveOperationException
    {
        return Class.forName(ACTION_CLASS).getField(simulate ? "SIMULATE" : "PERFORM").get(null);
    }

    static int insertIntoNetwork(Object network, ItemStack input, boolean simulate)
            throws ReflectiveOperationException
    {
        Object result = invokeCompatible(network, "insertItem", input.copy(), input.getCount(), action(simulate));
        return result instanceof ItemStack remainder
                ? Math.max(0, Math.min(input.getCount(), input.getCount() - remainder.getCount())) : 0;
    }

    static ItemStack extractFromNetwork(Object network, ItemStack prototype, long maxAmount, boolean simulate)
            throws ReflectiveOperationException
    {
        int count = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, maxAmount));
        if (count == 0)
        {
            return ItemStack.EMPTY;
        }
        Object result = invokeCompatible(network, "extractItem", prototype.copy(), count, action(simulate));
        return result instanceof ItemStack stack ? stack : ItemStack.EMPTY;
    }
}
