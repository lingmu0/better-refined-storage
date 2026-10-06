package net.xuwu.bettersophisticatedstorage;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.xuwu.bettersophisticatedstorage.client.ClientStorageState;
import net.xuwu.bettersophisticatedstorage.client.SidebarSettingsStore;
import net.xuwu.bettersophisticatedstorage.common.NetworkStorage;
import net.xuwu.bettersophisticatedstorage.common.StorageActions;
import net.xuwu.bettersophisticatedstorage.common.StorageEntry;
import net.xuwu.bettersophisticatedstorage.common.StorageSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Forge 1.20.1 packet channel for the portable-terminal storage sidebar. */
public final class NetworkHandler
{
    private static final String PROTOCOL_VERSION = "1";
    private static final int MAX_SYNC_PACKET_BYTES = 921600;
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            BetterSophisticatedStorage.id("main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );
    private static final Map<ServerPlayer, StorageSnapshot> LAST_SNAPSHOTS = new WeakHashMap<>();
    private static int nextId;
    private static long nextSyncSequence;

    private NetworkHandler()
    {
    }

    public static void register()
    {
        CHANNEL.registerMessage(nextId++, RequestSnapshotPacket.class,
                (packet, buffer) -> { }, buffer -> new RequestSnapshotPacket(),
                NetworkHandler::handleRequestSnapshot,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, SetShiftSettingsPacket.class,
                (packet, buffer) -> {
                    buffer.writeBoolean(packet.playerShift);
                    buffer.writeBoolean(packet.containerShift);
                    buffer.writeBoolean(packet.sidebarDisabled);
                    buffer.writeBoolean(packet.sidebarHidden);
                },
                buffer -> new SetShiftSettingsPacket(buffer.readBoolean(), buffer.readBoolean(),
                        buffer.readBoolean(), buffer.readBoolean()),
                NetworkHandler::handleSetShiftSettings,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, SnapshotPacket.class,
                NetworkHandler::encodeSnapshot, NetworkHandler::decodeSnapshot,
                NetworkHandler::handleSnapshot,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextId++, TogglePacket.class,
                (packet, buffer) -> buffer.writeVarInt(packet.target),
                buffer -> new TogglePacket(buffer.readVarInt()),
                NetworkHandler::handleToggle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, DepositPacket.class,
                (packet, buffer) -> {
                    buffer.writeVarInt(packet.target);
                    buffer.writeBoolean(packet.shortcut);
                },
                buffer -> new DepositPacket(buffer.readVarInt(), buffer.readBoolean()),
                NetworkHandler::handleDeposit,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, SidebarMenuClickPacket.class,
                (packet, buffer) -> {
                    buffer.writeVarInt(packet.slotId);
                    buffer.writeVarInt(packet.button);
                    buffer.writeVarInt(packet.clickType);
                },
                buffer -> new SidebarMenuClickPacket(buffer.readVarInt(), buffer.readVarInt(),
                        buffer.readVarInt()),
                NetworkHandler::handleSidebarMenuClick,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, SidebarViewPacket.class,
                (packet, buffer) -> {
                    int count = Math.min(40, packet.stacks.size());
                    buffer.writeVarInt(count);
                    for (int index = 0; index < count; index++)
                    {
                        buffer.writeItem(packet.stacks.get(index));
                    }
                },
                buffer -> {
                    int count = Math.min(40, Math.max(0, buffer.readVarInt()));
                    List<ItemStack> stacks = new ArrayList<>(count);
                    for (int index = 0; index < count; index++)
                    {
                        stacks.add(buffer.readItem());
                    }
                    return new SidebarViewPacket(stacks);
                },
                NetworkHandler::handleSidebarView,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void requestSnapshot()
    {
        SidebarSettingsStore.Settings settings = SidebarSettingsStore.get();
        ClientStorageState.setSidebarHidden(settings.sidebarHidden());
        sendShiftSettings(settings.playerShift(), settings.containerShift(), settings.sidebarDisabled(),
                settings.sidebarHidden());
        CHANNEL.sendToServer(new RequestSnapshotPacket());
    }

    public static void togglePlayerShift()
    {
        SidebarSettingsStore.Settings settings = SidebarSettingsStore.get();
        setShiftSettings(!settings.playerShift(), settings.containerShift());
    }

    public static void toggleContainerShift()
    {
        SidebarSettingsStore.Settings settings = SidebarSettingsStore.get();
        setShiftSettings(settings.playerShift(), !settings.containerShift());
    }

    public static void setShiftSettings(boolean playerShift, boolean containerShift)
    {
        SidebarSettingsStore.Settings settings = SidebarSettingsStore.get();
        SidebarSettingsStore.set(playerShift, containerShift, settings.sidebarHidden(),
                settings.sidebarDisabled());
        sendShiftSettings(playerShift, containerShift, settings.sidebarDisabled(), settings.sidebarHidden());
        CHANNEL.sendToServer(new RequestSnapshotPacket());
    }

    public static void setSidebarDisabled(boolean disabled)
    {
        SidebarSettingsStore.Settings settings = SidebarSettingsStore.get();
        SidebarSettingsStore.set(settings.playerShift(), settings.containerShift(),
                settings.sidebarHidden(), disabled);
        sendShiftSettings(settings.playerShift(), settings.containerShift(), disabled, settings.sidebarHidden());
        CHANNEL.sendToServer(new RequestSnapshotPacket());
    }

    private static void sendShiftSettings(boolean playerShift, boolean containerShift,
                                          boolean sidebarDisabled, boolean sidebarHidden)
    {
        CHANNEL.sendToServer(new SetShiftSettingsPacket(playerShift, containerShift, sidebarDisabled,
                sidebarHidden));
    }

    public static void setSidebarHidden(boolean hidden)
    {
        SidebarSettingsStore.setSidebarHidden(hidden);
        ClientStorageState.setSidebarHidden(hidden);
        CHANNEL.sendToServer(new TogglePacket(hidden
                ? StorageActions.HIDE_SIDEBAR
                : StorageActions.SHOW_SIDEBAR));
    }

    public static void depositContainer()
    {
        CHANNEL.sendToServer(new DepositPacket(StorageActions.DEPOSIT_CONTAINER, false));
    }

    public static void depositContainerShortcut()
    {
        CHANNEL.sendToServer(new DepositPacket(StorageActions.DEPOSIT_CONTAINER, true));
    }

    public static void depositPlayerInventory()
    {
        CHANNEL.sendToServer(new DepositPacket(StorageActions.DEPOSIT_PLAYER_INVENTORY, false));
    }

    public static void depositPlayerInventoryShortcut()
    {
        CHANNEL.sendToServer(new DepositPacket(StorageActions.DEPOSIT_PLAYER_INVENTORY, true));
    }

    public static void clickSidebarSlot(int slotId, int button, ClickType clickType)
    {
        if (clickType != null)
        {
            CHANNEL.sendToServer(new SidebarMenuClickPacket(slotId, button, clickType.ordinal()));
        }
    }

    public static void updateSidebarView(List<ItemStack> stacks)
    {
        List<ItemStack> values = new ArrayList<>(Math.min(40, stacks == null ? 0 : stacks.size()));
        if (stacks != null)
        {
            for (int index = 0; index < Math.min(40, stacks.size()); index++)
            {
                ItemStack stack = stacks.get(index);
                values.add(stack == null ? ItemStack.EMPTY : stack.copy());
            }
        }
        CHANNEL.sendToServer(new SidebarViewPacket(values));
    }

    private static void handleRequestSnapshot(RequestSnapshotPacket packet,
                                              Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null)
            {
                sendSnapshot(player);
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleSetShiftSettings(SetShiftSettingsPacket packet,
                                                Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null)
            {
                StorageActions.setShiftSettings(player, packet.playerShift, packet.containerShift,
                        packet.sidebarDisabled, packet.sidebarHidden);
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleSnapshot(SnapshotPacket packet,
                                       Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientStorageState.applySnapshotChunk(
                        packet.sequence, packet.chunkIndex, packet.chunkCount, packet.snapshot)));
        context.setPacketHandled(true);
    }

    private static void handleToggle(TogglePacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null)
            {
                StorageActions.toggle(player, packet.target);
                sendSnapshot(player);
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleDeposit(DepositPacket packet,
                                      Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null)
            {
                return;
            }
            if (packet.target == StorageActions.DEPOSIT_CONTAINER)
            {
                if (packet.shortcut)
                {
                    StorageActions.depositContainerShortcut(player);
                }
                else
                {
                    StorageActions.depositContainer(player);
                }
            }
            else if (packet.target == StorageActions.DEPOSIT_PLAYER_INVENTORY)
            {
                if (packet.shortcut)
                {
                    StorageActions.depositPlayerInventoryShortcut(player);
                }
                else
                {
                    StorageActions.depositPlayerInventory(player);
                }
            }
            sendSnapshot(player);
        });
        context.setPacketHandled(true);
    }

    private static void handleSidebarMenuClick(SidebarMenuClickPacket packet,
                                               Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null)
            {
                return;
            }
            ClickType[] values = ClickType.values();
            if (packet.clickType < 0 || packet.clickType >= values.length)
            {
                return;
            }
            StorageActions.handleSidebarClick(player, packet.slotId, packet.button,
                    values[packet.clickType]);
            sendSnapshot(player);
        });
        context.setPacketHandled(true);
    }

    private static void handleSidebarView(SidebarViewPacket packet,
                                          Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null)
            {
                StorageActions.updateSidebarView(player, packet.stacks);
            }
        });
        context.setPacketHandled(true);
    }

    public static void sendSnapshot(ServerPlayer player)
    {
        if (player == null)
        {
            return;
        }
        StorageActions.refreshSidebarSlots(player);
        StorageSnapshot snapshot = NetworkStorage.snapshot(player);
        LAST_SNAPSHOTS.put(player, snapshot);
        sendSnapshotChunks(player, snapshot);
    }

    /** Polls the portable network occasionally so changes made by other machines appear in the UI. */
    public static void tick(ServerPlayer player)
    {
        if (player == null || player.tickCount % 5 != 0)
        {
            return;
        }
        StorageSnapshot previous = LAST_SNAPSHOTS.get(player);
        if (previous == null)
        {
            return;
        }
        StorageSnapshot current = NetworkStorage.snapshot(player);
        if (!sameSnapshot(previous, current))
        {
            StorageActions.refreshSidebarSlots(player);
            LAST_SNAPSHOTS.put(player, current);
            sendSnapshotChunks(player, current);
        }
        else
        {
            StorageActions.refreshSidebarSlots(player);
        }
    }

    private static boolean sameSnapshot(StorageSnapshot first, StorageSnapshot second)
    {
        if (first == null || second == null
                || first.available() != second.available()
                || !first.networkName().equals(second.networkName())
                || first.shiftPlayerInventory() != second.shiftPlayerInventory()
                || first.shiftContainer() != second.shiftContainer()
                || first.sidebarHidden() != second.sidebarHidden()
                || first.entries().size() != second.entries().size())
        {
            return false;
        }
        for (int index = 0; index < first.entries().size(); index++)
        {
            StorageEntry left = first.entries().get(index);
            StorageEntry right = second.entries().get(index);
            if (left == null || right == null || left.amount() != right.amount()
                    || !sameItemAndComponents(left.stack(), right.stack()))
            {
                return false;
            }
        }
        return true;
    }

    private static boolean sameItemAndComponents(ItemStack first, ItemStack second)
    {
        if (first == null || second == null || first.isEmpty() || second.isEmpty())
        {
            return first == second || (first != null && second != null && first.isEmpty() && second.isEmpty());
        }
        return ItemStack.isSameItemSameTags(first, second);
    }

    private static void sendSnapshotChunks(ServerPlayer player, StorageSnapshot snapshot)
    {
        List<List<StorageEntry>> chunks = splitEntries(snapshot.entries());
        long sequence = ++nextSyncSequence;
        for (int index = 0; index < chunks.size(); index++)
        {
            StorageSnapshot chunk = new StorageSnapshot(snapshot.available(), snapshot.networkName(),
                    snapshot.shiftPlayerInventory(), snapshot.shiftContainer(), snapshot.sidebarHidden(),
                    chunks.get(index));
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new SnapshotPacket(sequence, index, chunks.size(), chunk));
        }
    }

    private static List<List<StorageEntry>> splitEntries(List<StorageEntry> entries)
    {
        List<List<StorageEntry>> chunks = new ArrayList<>();
        List<StorageEntry> current = new ArrayList<>();
        int currentBytes = 0;
        if (entries != null)
        {
            for (StorageEntry entry : entries)
            {
                if (entry == null || entry.stack().isEmpty())
                {
                    continue;
                }
                int entryBytes = estimateEntryBytes(entry);
                if (!current.isEmpty() && currentBytes + entryBytes > MAX_SYNC_PACKET_BYTES - 1024)
                {
                    chunks.add(List.copyOf(current));
                    current = new ArrayList<>();
                    currentBytes = 0;
                }
                current.add(entry);
                currentBytes += entryBytes;
            }
        }
        if (!current.isEmpty() || chunks.isEmpty())
        {
            chunks.add(List.copyOf(current));
        }
        return chunks;
    }

    private static int estimateEntryBytes(StorageEntry entry)
    {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try
        {
            buffer.writeItem(entry.stack());
            buffer.writeLong(entry.amount());
            return Math.max(1, buffer.readableBytes());
        }
        finally
        {
            buffer.release();
        }
    }

    private static void encodeSnapshot(SnapshotPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeLong(packet.sequence);
        buffer.writeVarInt(packet.chunkIndex);
        buffer.writeVarInt(packet.chunkCount);
        StorageSnapshot snapshot = packet.snapshot;
        buffer.writeBoolean(snapshot.available());
        buffer.writeUtf(snapshot.networkName(), 256);
        buffer.writeBoolean(snapshot.shiftPlayerInventory());
        buffer.writeBoolean(snapshot.shiftContainer());
        buffer.writeBoolean(snapshot.sidebarHidden());
        buffer.writeVarInt(snapshot.entries().size());
        for (StorageEntry entry : snapshot.entries())
        {
            buffer.writeItem(entry.stack());
            buffer.writeLong(entry.amount());
        }
    }

    private static SnapshotPacket decodeSnapshot(FriendlyByteBuf buffer)
    {
        long sequence = buffer.readLong();
        int chunkIndex = Math.max(0, buffer.readVarInt());
        int chunkCount = Math.max(1, buffer.readVarInt());
        boolean available = buffer.readBoolean();
        String networkName = buffer.readUtf(256);
        boolean shiftPlayer = buffer.readBoolean();
        boolean shiftContainer = buffer.readBoolean();
        boolean sidebarHidden = buffer.readBoolean();
        int count = Math.max(0, buffer.readVarInt());
        List<StorageEntry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++)
        {
            entries.add(new StorageEntry(buffer.readItem(), buffer.readLong()));
        }
        return new SnapshotPacket(sequence, chunkIndex, chunkCount,
                new StorageSnapshot(available, networkName, shiftPlayer, shiftContainer,
                        sidebarHidden, entries));
    }

    private static final class RequestSnapshotPacket
    {
    }

    private static final class SetShiftSettingsPacket
    {
        private final boolean playerShift;
        private final boolean containerShift;
        private final boolean sidebarDisabled;
        private final boolean sidebarHidden;

        private SetShiftSettingsPacket(boolean playerShift, boolean containerShift,
                                       boolean sidebarDisabled, boolean sidebarHidden)
        {
            this.playerShift = playerShift;
            this.containerShift = containerShift;
            this.sidebarDisabled = sidebarDisabled;
            this.sidebarHidden = sidebarHidden;
        }
    }

    private static final class SnapshotPacket
    {
        private final long sequence;
        private final int chunkIndex;
        private final int chunkCount;
        private final StorageSnapshot snapshot;

        private SnapshotPacket(long sequence, int chunkIndex, int chunkCount, StorageSnapshot snapshot)
        {
            this.sequence = sequence;
            this.chunkIndex = chunkIndex;
            this.chunkCount = chunkCount;
            this.snapshot = snapshot;
        }
    }

    private static final class TogglePacket
    {
        private final int target;

        private TogglePacket(int target)
        {
            this.target = target;
        }
    }

    private static final class DepositPacket
    {
        private final int target;
        private final boolean shortcut;

        private DepositPacket(int target, boolean shortcut)
        {
            this.target = target;
            this.shortcut = shortcut;
        }
    }

    private static final class SidebarMenuClickPacket
    {
        private final int slotId;
        private final int button;
        private final int clickType;

        private SidebarMenuClickPacket(int slotId, int button, int clickType)
        {
            this.slotId = slotId;
            this.button = button;
            this.clickType = clickType;
        }
    }

    private static final class SidebarViewPacket
    {
        private final List<ItemStack> stacks;

        private SidebarViewPacket(List<ItemStack> stacks)
        {
            this.stacks = stacks == null ? List.of() : List.copyOf(stacks);
        }
    }
}
