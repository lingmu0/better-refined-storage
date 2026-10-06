package net.xuwu.bettersophisticatedstorage;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.xuwu.bettersophisticatedstorage.client.ClientStorageState;
import net.xuwu.bettersophisticatedstorage.client.SidebarSettingsStore;
import net.xuwu.bettersophisticatedstorage.common.NetworkStorage;
import net.xuwu.bettersophisticatedstorage.common.StorageActions;
import net.xuwu.bettersophisticatedstorage.common.StorageEntry;
import net.xuwu.bettersophisticatedstorage.common.StorageSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** NeoForge 1.21.1 custom payloads for the portable-terminal storage sidebar. */
public final class NetworkHandler
{
    private static final int MAX_SYNC_PACKET_BYTES = 921600;
    private static final Map<ServerPlayer, StorageSnapshot> LAST_SNAPSHOTS = new WeakHashMap<>();
    private static long nextSyncSequence;

    private NetworkHandler()
    {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event)
    {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playBidirectional(RequestSnapshotPacket.TYPE, RequestSnapshotPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(RequestSnapshotPacket::handle, RequestSnapshotPacket::handle));
        registrar.playBidirectional(SetShiftSettingsPacket.TYPE, SetShiftSettingsPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(SetShiftSettingsPacket::handle, SetShiftSettingsPacket::handle));
        registrar.playBidirectional(SnapshotPacket.TYPE, SnapshotPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(SnapshotPacket::handle, SnapshotPacket::handle));
        registrar.playBidirectional(TogglePacket.TYPE, TogglePacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(TogglePacket::handle, TogglePacket::handle));
        registrar.playBidirectional(DepositPacket.TYPE, DepositPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(DepositPacket::handle, DepositPacket::handle));
        registrar.playBidirectional(SidebarMenuClickPacket.TYPE, SidebarMenuClickPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(SidebarMenuClickPacket::handle, SidebarMenuClickPacket::handle));
        registrar.playBidirectional(SidebarViewPacket.TYPE, SidebarViewPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(SidebarViewPacket::handle, SidebarViewPacket::handle));
    }

    public static void requestSnapshot()
    {
        SidebarSettingsStore.Settings settings = SidebarSettingsStore.get();
        ClientStorageState.setSidebarHidden(settings.sidebarHidden());
        sendShiftSettings(settings.playerShift(), settings.containerShift(), settings.sidebarDisabled(),
                settings.sidebarHidden());
        PacketDistributor.sendToServer(new RequestSnapshotPacket());
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
        PacketDistributor.sendToServer(new RequestSnapshotPacket());
    }

    public static void setSidebarDisabled(boolean disabled)
    {
        SidebarSettingsStore.Settings settings = SidebarSettingsStore.get();
        SidebarSettingsStore.set(settings.playerShift(), settings.containerShift(),
                settings.sidebarHidden(), disabled);
        sendShiftSettings(settings.playerShift(), settings.containerShift(), disabled, settings.sidebarHidden());
        PacketDistributor.sendToServer(new RequestSnapshotPacket());
    }

    private static void sendShiftSettings(boolean playerShift, boolean containerShift,
                                          boolean sidebarDisabled, boolean sidebarHidden)
    {
        PacketDistributor.sendToServer(new SetShiftSettingsPacket(playerShift, containerShift,
                sidebarDisabled, sidebarHidden));
    }

    public static void setSidebarHidden(boolean hidden)
    {
        SidebarSettingsStore.setSidebarHidden(hidden);
        ClientStorageState.setSidebarHidden(hidden);
        PacketDistributor.sendToServer(new TogglePacket(hidden
                ? StorageActions.HIDE_SIDEBAR
                : StorageActions.SHOW_SIDEBAR));
    }

    public static void depositContainer()
    {
        PacketDistributor.sendToServer(new DepositPacket(StorageActions.DEPOSIT_CONTAINER, false));
    }

    public static void depositContainerShortcut()
    {
        PacketDistributor.sendToServer(new DepositPacket(StorageActions.DEPOSIT_CONTAINER, true));
    }

    public static void depositPlayerInventory()
    {
        PacketDistributor.sendToServer(new DepositPacket(StorageActions.DEPOSIT_PLAYER_INVENTORY, false));
    }

    public static void depositPlayerInventoryShortcut()
    {
        PacketDistributor.sendToServer(new DepositPacket(StorageActions.DEPOSIT_PLAYER_INVENTORY, true));
    }

    public static void clickSidebarSlot(int slotId, int button, ClickType clickType)
    {
        if (clickType != null)
        {
            PacketDistributor.sendToServer(new SidebarMenuClickPacket(slotId, button, clickType.ordinal()));
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
        PacketDistributor.sendToServer(new SidebarViewPacket(values));
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
        return ItemStack.isSameItemSameComponents(first, second);
    }

    private static void sendSnapshotChunks(ServerPlayer player, StorageSnapshot snapshot)
    {
        List<List<StorageEntry>> chunks = splitEntries(player, snapshot.entries());
        long sequence = ++nextSyncSequence;
        for (int index = 0; index < chunks.size(); index++)
        {
            StorageSnapshot chunk = new StorageSnapshot(snapshot.available(), snapshot.networkName(),
                    snapshot.shiftPlayerInventory(), snapshot.shiftContainer(), snapshot.sidebarHidden(),
                    chunks.get(index));
            PacketDistributor.sendToPlayer(player,
                    new SnapshotPacket(sequence, index, chunks.size(), chunk));
        }
    }

    private static List<List<StorageEntry>> splitEntries(ServerPlayer player, List<StorageEntry> entries)
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
                int entryBytes = estimateEntryBytes(player, entry);
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

    private static int estimateEntryBytes(ServerPlayer player, StorageEntry entry)
    {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), player.level().registryAccess());
        try
        {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, entry.stack());
            buffer.writeLong(entry.amount());
            return Math.max(1, buffer.readableBytes());
        }
        finally
        {
            buffer.release();
        }
    }

    private static StorageSnapshot readSnapshot(RegistryFriendlyByteBuf buffer)
    {
        boolean available = buffer.readBoolean();
        String networkName = buffer.readUtf(256);
        boolean shiftPlayer = buffer.readBoolean();
        boolean shiftContainer = buffer.readBoolean();
        boolean sidebarHidden = buffer.readBoolean();
        int count = Math.max(0, buffer.readVarInt());
        List<StorageEntry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++)
        {
            ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
            entries.add(new StorageEntry(stack, buffer.readLong()));
        }
        return new StorageSnapshot(available, networkName, shiftPlayer, shiftContainer,
                sidebarHidden, entries);
    }

    private static void writeSnapshot(RegistryFriendlyByteBuf buffer, StorageSnapshot snapshot)
    {
        buffer.writeBoolean(snapshot.available());
        buffer.writeUtf(snapshot.networkName(), 256);
        buffer.writeBoolean(snapshot.shiftPlayerInventory());
        buffer.writeBoolean(snapshot.shiftContainer());
        buffer.writeBoolean(snapshot.sidebarHidden());
        buffer.writeVarInt(snapshot.entries().size());
        for (StorageEntry entry : snapshot.entries())
        {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, entry.stack());
            buffer.writeLong(entry.amount());
        }
    }

    private record RequestSnapshotPacket() implements CustomPacketPayload
    {
        private static final Type<RequestSnapshotPacket> TYPE = new Type<>(
                BetterSophisticatedStorage.id("request_snapshot"));
        private static final StreamCodec<ByteBuf, RequestSnapshotPacket> STREAM_CODEC =
                StreamCodec.unit(new RequestSnapshotPacket());

        private static void handle(RequestSnapshotPacket packet, IPayloadContext context)
        {
            if (context.flow() == PacketFlow.SERVERBOUND)
            {
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player)
                    {
                        sendSnapshot(player);
                    }
                });
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    private record SnapshotPacket(long sequence, int chunkIndex, int chunkCount,
                                  StorageSnapshot snapshot) implements CustomPacketPayload
    {
        private static final Type<SnapshotPacket> TYPE = new Type<>(
                BetterSophisticatedStorage.id("snapshot"));
        private static final StreamCodec<RegistryFriendlyByteBuf, SnapshotPacket> STREAM_CODEC =
                new StreamCodec<>()
                {
                    @Override
                    public void encode(RegistryFriendlyByteBuf buffer, SnapshotPacket packet)
                    {
                        buffer.writeLong(packet.sequence);
                        buffer.writeVarInt(packet.chunkIndex);
                        buffer.writeVarInt(packet.chunkCount);
                        writeSnapshot(buffer, packet.snapshot);
                    }

                    @Override
                    public SnapshotPacket decode(RegistryFriendlyByteBuf buffer)
                    {
                        return new SnapshotPacket(buffer.readLong(), Math.max(0, buffer.readVarInt()),
                                Math.max(1, buffer.readVarInt()), readSnapshot(buffer));
                    }
                };

        private static void handle(SnapshotPacket packet, IPayloadContext context)
        {
            if (context.flow() == PacketFlow.CLIENTBOUND)
            {
                context.enqueueWork(() -> ClientStorageState.applySnapshotChunk(
                        packet.sequence, packet.chunkIndex, packet.chunkCount, packet.snapshot));
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    private record SetShiftSettingsPacket(boolean playerShift, boolean containerShift,
                                          boolean sidebarDisabled, boolean sidebarHidden)
            implements CustomPacketPayload
    {
        private static final Type<SetShiftSettingsPacket> TYPE = new Type<>(
                BetterSophisticatedStorage.id("set_shift_settings"));
        private static final StreamCodec<RegistryFriendlyByteBuf, SetShiftSettingsPacket> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BOOL, SetShiftSettingsPacket::playerShift,
                        ByteBufCodecs.BOOL, SetShiftSettingsPacket::containerShift,
                        ByteBufCodecs.BOOL, SetShiftSettingsPacket::sidebarDisabled,
                        ByteBufCodecs.BOOL, SetShiftSettingsPacket::sidebarHidden,
                        SetShiftSettingsPacket::new
                );

        private static void handle(SetShiftSettingsPacket packet, IPayloadContext context)
        {
            if (context.flow() == PacketFlow.SERVERBOUND)
            {
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player)
                    {
                        StorageActions.setShiftSettings(player, packet.playerShift, packet.containerShift,
                                packet.sidebarDisabled, packet.sidebarHidden);
                    }
                });
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    private record TogglePacket(int target) implements CustomPacketPayload
    {
        private static final Type<TogglePacket> TYPE = new Type<>(BetterSophisticatedStorage.id("toggle"));
        private static final StreamCodec<RegistryFriendlyByteBuf, TogglePacket> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, TogglePacket::target, TogglePacket::new);

        private static void handle(TogglePacket packet, IPayloadContext context)
        {
            if (context.flow() == PacketFlow.SERVERBOUND)
            {
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player)
                    {
                        StorageActions.toggle(player, packet.target);
                        sendSnapshot(player);
                    }
                });
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    private record DepositPacket(int target, boolean shortcut) implements CustomPacketPayload
    {
        private static final Type<DepositPacket> TYPE = new Type<>(BetterSophisticatedStorage.id("deposit"));
        private static final StreamCodec<RegistryFriendlyByteBuf, DepositPacket> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, DepositPacket::target,
                        ByteBufCodecs.BOOL, DepositPacket::shortcut,
                        DepositPacket::new);

        private static void handle(DepositPacket packet, IPayloadContext context)
        {
            if (context.flow() == PacketFlow.SERVERBOUND)
            {
                context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer player))
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
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    private record SidebarMenuClickPacket(int slotId, int button, int clickType)
            implements CustomPacketPayload
    {
        private static final Type<SidebarMenuClickPacket> TYPE = new Type<>(
                BetterSophisticatedStorage.id("sidebar_menu_click"));
        private static final StreamCodec<RegistryFriendlyByteBuf, SidebarMenuClickPacket> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, SidebarMenuClickPacket::slotId,
                        ByteBufCodecs.VAR_INT, SidebarMenuClickPacket::button,
                        ByteBufCodecs.VAR_INT, SidebarMenuClickPacket::clickType,
                        SidebarMenuClickPacket::new
                );

        private static void handle(SidebarMenuClickPacket packet, IPayloadContext context)
        {
            if (context.flow() == PacketFlow.SERVERBOUND)
            {
                context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer player))
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
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    private record SidebarViewPacket(List<ItemStack> stacks) implements CustomPacketPayload
    {
        private static final Type<SidebarViewPacket> TYPE = new Type<>(
                BetterSophisticatedStorage.id("sidebar_view"));
        private static final StreamCodec<RegistryFriendlyByteBuf, SidebarViewPacket> STREAM_CODEC =
                new StreamCodec<>()
                {
                    @Override
                    public void encode(RegistryFriendlyByteBuf buffer, SidebarViewPacket packet)
                    {
                        List<ItemStack> values = packet.stacks == null ? List.of() : packet.stacks;
                        int count = Math.min(40, values.size());
                        buffer.writeVarInt(count);
                        for (int index = 0; index < count; index++)
                        {
                            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, values.get(index));
                        }
                    }

                    @Override
                    public SidebarViewPacket decode(RegistryFriendlyByteBuf buffer)
                    {
                        int count = Math.min(40, Math.max(0, buffer.readVarInt()));
                        List<ItemStack> values = new ArrayList<>(count);
                        for (int index = 0; index < count; index++)
                        {
                            values.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
                        }
                        return new SidebarViewPacket(values);
                    }
                };

        private static void handle(SidebarViewPacket packet, IPayloadContext context)
        {
            if (context.flow() == PacketFlow.SERVERBOUND)
            {
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player)
                    {
                        StorageActions.updateSidebarView(player, packet.stacks);
                    }
                });
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }
}
