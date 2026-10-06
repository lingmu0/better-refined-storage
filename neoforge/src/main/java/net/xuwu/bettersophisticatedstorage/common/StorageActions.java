package net.xuwu.bettersophisticatedstorage.common;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** All sidebar mutations, including one-click deposit, run on the logical server. */
public final class StorageActions
{
    private static final int PLAYER_ITEM_SLOT_COUNT = 36;

    public static final int TOGGLE_PLAYER_SHIFT = 0;
    public static final int TOGGLE_CONTAINER_SHIFT = 1;
    public static final int DEPOSIT_CONTAINER = 2;
    public static final int DEPOSIT_PLAYER_INVENTORY = 3;
    public static final int HIDE_SIDEBAR = 4;
    public static final int SHOW_SIDEBAR = 5;

    private static final String SHIFT_PLAYER_TAG = "better_sophisticated_storage.shift_player_inventory";
    private static final String SHIFT_CONTAINER_TAG = "better_sophisticated_storage.shift_container";
    private static final String SIDEBAR_HIDDEN_TAG = "better_sophisticated_storage.sidebar_hidden";
    private static final String SIDEBAR_DISABLED_TAG = "better_sophisticated_storage.sidebar_disabled";

    private StorageActions()
    {
    }

    public static boolean isShiftPlayerInventoryEnabled(Player player)
    {
        return player != null && player.getPersistentData().getBoolean(SHIFT_PLAYER_TAG);
    }

    public static boolean isShiftContainerEnabled(Player player)
    {
        return player != null && player.getPersistentData().getBoolean(SHIFT_CONTAINER_TAG);
    }

    public static void setShiftSettings(Player player, boolean shiftPlayer, boolean shiftContainer)
    {
        setShiftSettings(player, shiftPlayer, shiftContainer, isSidebarDisabled(player), isSidebarHidden(player));
    }

    public static void setShiftSettings(Player player, boolean shiftPlayer, boolean shiftContainer,
                                        boolean sidebarDisabled, boolean sidebarHidden)
    {
        if (player == null)
        {
            return;
        }
        CompoundTag data = player.getPersistentData();
        data.putBoolean(SHIFT_PLAYER_TAG, shiftPlayer);
        data.putBoolean(SHIFT_CONTAINER_TAG, shiftContainer);
        data.putBoolean(SIDEBAR_DISABLED_TAG, sidebarDisabled);
        data.putBoolean(SIDEBAR_HIDDEN_TAG, sidebarHidden);
    }

    public static boolean isSidebarHidden(Player player)
    {
        return player != null && player.getPersistentData().getBoolean(SIDEBAR_HIDDEN_TAG);
    }

    public static boolean isSidebarDisabled(Player player)
    {
        return player != null && player.getPersistentData().getBoolean(SIDEBAR_DISABLED_TAG);
    }

    public static void setSidebarHidden(Player player, boolean hidden)
    {
        if (player == null)
        {
            return;
        }
        player.getPersistentData().putBoolean(SIDEBAR_HIDDEN_TAG, hidden);
        if (hidden && player instanceof ServerPlayer serverPlayer)
        {
            clearSidebarSlots(serverPlayer);
        }
    }

    public static boolean toggle(Player player, int target)
    {
        if (target == HIDE_SIDEBAR || target == SHOW_SIDEBAR)
        {
            boolean hidden = target == HIDE_SIDEBAR;
            setSidebarHidden(player, hidden);
            return hidden;
        }
        if (isSidebarHidden(player))
        {
            return false;
        }
        if (target == TOGGLE_PLAYER_SHIFT)
        {
            boolean next = !isShiftPlayerInventoryEnabled(player);
            setShiftSettings(player, next, isShiftContainerEnabled(player));
            return next;
        }
        if (target == TOGGLE_CONTAINER_SHIFT)
        {
            boolean next = !isShiftContainerEnabled(player);
            setShiftSettings(player, isShiftPlayerInventoryEnabled(player), next);
            return next;
        }
        return false;
    }

    public static void depositPlayerInventory(ServerPlayer player)
    {
        depositPlayerInventory(player, false);
    }

    public static void depositPlayerInventoryShortcut(ServerPlayer player)
    {
        depositPlayerInventory(player, true);
    }

    private static void depositPlayerInventory(ServerPlayer player, boolean shortcut)
    {
        if (!canUseDepositAction(player, shortcut))
        {
            return;
        }
        PortableStorageNetwork.Connection connection = PortableStorageNetwork.find(player);
        if (connection == null)
        {
            return;
        }

        Inventory inventory = player.getInventory();
        // Keep the wireless grid in place. This applies even when the grid is in the
        // ordinary main inventory; Curios slots are never part of this deposit pass.
        for (int index = 9; index < 36; index++)
        {
            ItemStack stack = inventory.getItem(index);
            if (!PortableStorageNetwork.isRefinedStorageWirelessGrid(stack))
            {
                depositStack(connection, stack);
            }
        }
        player.containerMenu.broadcastChanges();
    }

    public static void depositContainer(ServerPlayer player)
    {
        depositContainer(player, player == null ? null : player.containerMenu, false);
    }

    public static void depositContainerShortcut(ServerPlayer player)
    {
        depositContainer(player, player == null ? null : player.containerMenu, true);
    }

    private static void depositContainer(ServerPlayer player, AbstractContainerMenu menu, boolean shortcut)
    {
        if (!canUseDepositAction(player, shortcut) || menu == null)
        {
            return;
        }
        PortableStorageNetwork.Connection connection = PortableStorageNetwork.find(player);
        if (connection == null)
        {
            return;
        }

        Container playerInventory = player.getInventory();
        List<Slot> inputSlots = new ArrayList<>();
        List<Slot> normalSlots = new ArrayList<>();
        List<Slot> unknownSlots = new ArrayList<>();
        List<Slot> outputSlots = new ArrayList<>();
        for (Slot slot : menu.slots)
        {
            if (slot instanceof NetworkStorageSlot || slot.container == playerInventory || !slot.hasItem()
                    || PortableStorageNetwork.isRefinedStorageWirelessGrid(slot.getItem()))
            {
                continue;
            }
            switch (classifyDepositSlot(slot))
            {
                case INPUT -> inputSlots.add(slot);
                case NORMAL -> normalSlots.add(slot);
                case UNKNOWN -> unknownSlots.add(slot);
                case OUTPUT -> outputSlots.add(slot);
            }
        }

        for (List<Slot> slots : List.of(inputSlots, normalSlots, unknownSlots))
        {
            for (Slot slot : slots)
            {
                transferSlotToNetwork(player, menu, slot, connection);
            }
        }
        for (Slot slot : outputSlots)
        {
            if (slot instanceof ResultSlot resultSlot)
            {
                quickCraftResultToNetwork(player, menu, resultSlot, connection);
            }
            else
            {
                quickTransferOutputToNetwork(player, menu, slot, connection);
            }
        }
        menu.broadcastChanges();
    }

    private static DepositSlotKind classifyDepositSlot(Slot slot)
    {
        if (slot instanceof ResultSlot || !slot.mayPlace(slot.getItem()))
        {
            return DepositSlotKind.OUTPUT;
        }
        if (slot.container instanceof CraftingContainer)
        {
            return DepositSlotKind.INPUT;
        }
        if (slot.getClass() == Slot.class)
        {
            return DepositSlotKind.NORMAL;
        }
        return DepositSlotKind.UNKNOWN;
    }

    private enum DepositSlotKind
    {
        INPUT, NORMAL, UNKNOWN, OUTPUT
    }

    /** Intercepts a vanilla shift-click only when the corresponding setting is enabled. */
    public static boolean routeQuickMove(ServerPlayer player, AbstractContainerMenu menu, int slotId)
    {
        if (player == null || menu == null || isSidebarHidden(player) || isSidebarDisabled(player)
                || (player.isCreative() && menu == player.inventoryMenu)
                || slotId < 0 || slotId >= menu.slots.size())
        {
            return false;
        }

        PortableStorageNetwork.Connection connection = PortableStorageNetwork.find(player);
        if (connection == null)
        {
            return false;
        }
        Slot slot = menu.slots.get(slotId);
        if (!slot.hasItem() || PortableStorageNetwork.isRefinedStorageWirelessGrid(slot.getItem()))
        {
            return false;
        }

        boolean fromPlayerInventory = slot.container == player.getInventory();
        boolean enabled = fromPlayerInventory
                ? isShiftPlayerInventoryEnabled(player) : isShiftContainerEnabled(player);
        if (!enabled)
        {
            return false;
        }

        if (slot instanceof ResultSlot resultSlot)
        {
            quickCraftResultToNetwork(player, menu, resultSlot, connection);
        }
        else if (!slot.mayPlace(slot.getItem()))
        {
            quickTransferOutputToNetwork(player, menu, slot, connection);
        }
        else
        {
            transferSlotToNetwork(player, menu, slot, connection);
        }
        menu.broadcastChanges();
        return true;
    }

    private static void quickTransferOutputToNetwork(ServerPlayer player, AbstractContainerMenu menu,
                                                     Slot slot, PortableStorageNetwork.Connection connection)
    {
        if (slot == null || !slot.hasItem())
        {
            return;
        }
        ItemStack initialOutput = slot.getItem().copy();
        for (int iteration = 0; iteration < 4096 && slot.hasItem(); iteration++)
        {
            if (!sameStoredStack(initialOutput, slot.getItem())
                    || transferSlotToNetwork(player, menu, slot, connection) <= 0)
            {
                break;
            }
        }
    }

    private static int transferSlotToNetwork(ServerPlayer player, AbstractContainerMenu menu,
                                              Slot slot, PortableStorageNetwork.Connection connection)
    {
        if (slot == null || !slot.hasItem() || !slot.mayPickup(player))
        {
            return 0;
        }
        ItemStack source = slot.getItem().copy();
        if (PortableStorageNetwork.isRefinedStorageWirelessGrid(source))
        {
            return 0;
        }

        int requested = source.getCount();
        int inserted = connection.insert(source, false);
        if (inserted <= 0)
        {
            return 0;
        }
        if (!slot.mayPlace(source) && inserted != requested)
        {
            connection.extract(source, inserted, false);
            return 0;
        }

        boolean useNativeTakeCallback = slot instanceof ResultSlot || !slot.mayPlace(source);
        ItemStack taken;
        if (useNativeTakeCallback)
        {
            // Output slots need their native callback for recipe/machine bookkeeping.
            taken = slot.safeTake(inserted, inserted, player);
        }
        else
        {
            // Ordinary modded slots may implement onTake as another transfer hook. The direct
            // remove path matches vanilla quick-move and avoids duplicating the inserted stack.
            taken = slot.remove(inserted);
            if (!taken.isEmpty())
            {
                if (slot.hasItem())
                {
                    slot.setChanged();
                }
                else
                {
                    slot.set(ItemStack.EMPTY);
                }
            }
        }
        int takenCount = taken.isEmpty() ? 0 : Math.min(inserted, taken.getCount());
        if (takenCount < inserted)
        {
            connection.extract(source, inserted - takenCount, false);
        }
        if (takenCount <= 0)
        {
            return 0;
        }
        slot.setChanged();
        menu.slotsChanged(slot.container);
        return takenCount;
    }

    private static void quickCraftResultToNetwork(ServerPlayer player, AbstractContainerMenu menu,
                                                  ResultSlot resultSlot,
                                                  PortableStorageNetwork.Connection connection)
    {
        for (int iteration = 0; iteration < 4096 && resultSlot.hasItem(); iteration++)
        {
            if (!resultSlot.mayPickup(player))
            {
                break;
            }
            ItemStack output = resultSlot.getItem().copy();
            if (PortableStorageNetwork.isRefinedStorageWirelessGrid(output))
            {
                break;
            }
            int outputCount = output.getCount();
            int inserted = connection.insert(output, false);
            if (inserted != outputCount)
            {
                if (inserted > 0)
                {
                    connection.extract(output, inserted, false);
                }
                break;
            }
            ItemStack taken = resultSlot.safeTake(outputCount, outputCount, player);
            if (taken.isEmpty())
            {
                connection.extract(output, outputCount, false);
                break;
            }
        }
        menu.broadcastChanges();
    }

    /** Applies the client-side filtered view to the menu's real sidebar slots. */
    public static void updateSidebarView(ServerPlayer player, List<ItemStack> viewStacks)
    {
        if (player == null || player.containerMenu == null
                || !(player.containerMenu instanceof NetworkStorageMenuAccess access))
        {
            return;
        }
        if (isSidebarHidden(player) || isSidebarDisabled(player))
        {
            clearSidebarSlots(player);
            return;
        }

        PortableStorageNetwork.Connection connection = PortableStorageNetwork.find(player);
        List<StorageEntry> entries = connection == null ? List.of() : connection.entries();
        List<NetworkStorageSlot> slots = access.bss$getNetworkSlots();
        for (int index = 0; index < slots.size(); index++)
        {
            ItemStack requested = viewStacks != null && index < viewStacks.size()
                    ? viewStacks.get(index) : ItemStack.EMPTY;
            StorageEntry stored = findEntry(entries, requested);
            if (connection == null || stored == null)
            {
                slots.get(index).clear();
            }
            else
            {
                slots.get(index).update(index, stored.stack(), stored.amount(), true);
            }
        }
        player.containerMenu.broadcastChanges();
    }

    /** Refreshes slot amounts after a storage mutation without trusting stale client amounts. */
    public static void refreshSidebarSlots(ServerPlayer player)
    {
        if (player == null || player.containerMenu == null
                || !(player.containerMenu instanceof NetworkStorageMenuAccess access))
        {
            return;
        }
        PortableStorageNetwork.Connection connection = isSidebarHidden(player) || isSidebarDisabled(player)
                ? null : PortableStorageNetwork.find(player);
        List<StorageEntry> entries = connection == null ? List.of() : connection.entries();
        for (NetworkStorageSlot slot : access.bss$getNetworkSlots())
        {
            ItemStack key = slot.getKey();
            StorageEntry stored = findEntry(entries, key);
            if (connection == null || !slot.isActive() || stored == null)
            {
                slot.clear();
            }
            else
            {
                slot.update(slot.getStorageIndex(), stored.stack(), stored.amount(), true);
            }
        }
        player.containerMenu.broadcastChanges();
    }

    public static void handleSidebarClick(ServerPlayer player, int slotId, int button, ClickType clickType)
    {
        if (player == null || isSidebarHidden(player) || isSidebarDisabled(player)
                || player.containerMenu == null || slotId < 0
                || slotId >= player.containerMenu.slots.size())
        {
            return;
        }

        Slot slot = player.containerMenu.slots.get(slotId);
        if (slot instanceof NetworkStorageSlot networkSlot)
        {
            handleSidebarClick(player, networkSlot, button, clickType);
        }
        else if (clickType == ClickType.QUICK_MOVE)
        {
            // The client routes enabled quick-moves here so a custom host menu cannot leave a
            // second, predicted transfer behind after the server-side network operation.
            routeQuickMove(player, player.containerMenu, slotId);
        }
    }

    public static void handleSidebarClick(ServerPlayer player, NetworkStorageSlot slot, int button,
                                          ClickType clickType)
    {
        if (player == null || isSidebarHidden(player) || isSidebarDisabled(player)
                || player.containerMenu == null || slot == null)
        {
            return;
        }
        switch (clickType)
        {
            case PICKUP -> clickSidebar(player, slot, button);
            case QUICK_MOVE -> quickMoveSidebar(player, slot);
            case THROW -> throwFromSidebar(player, slot, button);
            case PICKUP_ALL -> pickupAllFromSidebar(player, slot);
            case SWAP -> swapHotbarWithSidebar(player, slot, button);
            case QUICK_CRAFT, CLONE -> {
            }
        }
        player.containerMenu.broadcastChanges();
    }

    private static void clickSidebar(ServerPlayer player, NetworkStorageSlot slot, int button)
    {
        if (button != 0 && button != 1)
        {
            return;
        }
        PortableStorageNetwork.Connection connection = PortableStorageNetwork.find(player);
        if (connection == null)
        {
            return;
        }
        ItemStack carried = player.containerMenu.getCarried().copy();
        if (carried.isEmpty())
        {
            if (!slot.hasItem())
            {
                return;
            }
            ItemStack key = slot.getKey();
            long maxStack = Math.max(1L, key.getMaxStackSize());
            long request = Math.min(slot.getStoredAmount(), maxStack);
            long amount = button == 0 ? request : (request + 1L) / 2L;
            ItemStack extracted = connection.extract(key, amount, false);
            if (!extracted.isEmpty())
            {
                player.containerMenu.setCarried(extracted);
            }
        }
        else if (!PortableStorageNetwork.isRefinedStorageWirelessGrid(carried))
        {
            int request = button == 0 ? carried.getCount() : 1;
            int inserted = connection.insert(withCount(carried, request), false);
            if (inserted > 0)
            {
                carried.shrink(inserted);
                player.containerMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            }
        }
    }

    private static void quickMoveSidebar(ServerPlayer player, NetworkStorageSlot slot)
    {
        if (slot.getKey().isEmpty())
        {
            return;
        }
        PortableStorageNetwork.Connection connection = PortableStorageNetwork.find(player);
        if (connection == null)
        {
            return;
        }
        ItemStack key = slot.getKey();
        long group = Math.min(slot.getStoredAmount(), Math.max(1L, key.getMaxStackSize()));
        ItemStack extracted = connection.extract(key, group, false);
        if (extracted.isEmpty())
        {
            return;
        }
        int inserted = insertIntoPlayerInventory(player, extracted);
        if (inserted < extracted.getCount())
        {
            ItemStack rollback = extracted.copy();
            rollback.setCount(extracted.getCount() - inserted);
            connection.insert(rollback, false);
        }
    }

    private static void throwFromSidebar(ServerPlayer player, NetworkStorageSlot slot, int button)
    {
        if (slot.getKey().isEmpty())
        {
            return;
        }
        PortableStorageNetwork.Connection connection = PortableStorageNetwork.find(player);
        if (connection == null)
        {
            return;
        }
        long amount = button == 1
                ? Math.min(slot.getStoredAmount(), Math.max(1L, slot.getKey().getMaxStackSize())) : 1L;
        ItemStack extracted = connection.extract(slot.getKey(), amount, false);
        if (!extracted.isEmpty())
        {
            player.drop(extracted, true);
        }
    }

    private static void pickupAllFromSidebar(ServerPlayer player, NetworkStorageSlot slot)
    {
        ItemStack carried = player.containerMenu.getCarried().copy();
        if (carried.isEmpty())
        {
            return;
        }
        PortableStorageNetwork.Connection connection = PortableStorageNetwork.find(player);
        if (connection == null)
        {
            return;
        }
        long space = Math.max(0L, carried.getMaxStackSize() - (long) carried.getCount());
        ItemStack extracted = connection.extract(carried, space, false);
        if (!extracted.isEmpty())
        {
            carried.grow(extracted.getCount());
            player.containerMenu.setCarried(carried);
        }
    }

    private static void swapHotbarWithSidebar(ServerPlayer player, NetworkStorageSlot slot, int button)
    {
        if (button < 0 || button >= 9 || slot.getKey().isEmpty())
        {
            return;
        }
        PortableStorageNetwork.Connection connection = PortableStorageNetwork.find(player);
        if (connection == null)
        {
            return;
        }
        ItemStack hotbar = player.getInventory().getItem(button).copy();
        if (!hotbar.isEmpty() && !PortableStorageNetwork.isRefinedStorageWirelessGrid(hotbar))
        {
            int inserted = connection.insert(hotbar, false);
            if (inserted > 0)
            {
                hotbar.shrink(inserted);
                player.getInventory().setItem(button, hotbar);
            }
        }
        else if (hotbar.isEmpty())
        {
            quickMoveSidebar(player, slot);
        }
    }

    /** Used by Slot#remove for compatibility with mods that inspect the appended slots. */
    public static ItemStack extractFromSidebar(ServerPlayer player, NetworkStorageSlot slot, int amount)
    {
        if (player == null || slot == null || amount <= 0 || isSidebarHidden(player) || isSidebarDisabled(player))
        {
            return ItemStack.EMPTY;
        }
        PortableStorageNetwork.Connection connection = PortableStorageNetwork.find(player);
        if (connection == null || slot.getKey().isEmpty())
        {
            return ItemStack.EMPTY;
        }
        ItemStack extracted = connection.extract(slot.getKey(), amount, false);
        if (!extracted.isEmpty())
        {
            slot.reduceAmount(extracted.getCount());
        }
        return extracted;
    }

    private static int depositStack(PortableStorageNetwork.Connection connection, ItemStack stack)
    {
        if (stack == null || stack.isEmpty() || PortableStorageNetwork.isRefinedStorageWirelessGrid(stack))
        {
            return 0;
        }
        int inserted = connection.insert(stack, false);
        if (inserted > 0)
        {
            stack.shrink(inserted);
        }
        return inserted;
    }

    private static int insertIntoPlayerInventory(ServerPlayer player, ItemStack input)
    {
        if (input == null || input.isEmpty())
        {
            return 0;
        }
        Inventory inventory = player.getInventory();
        int remaining = input.getCount();
        int original = remaining;
        for (int index = 0; index < PLAYER_ITEM_SLOT_COUNT && remaining > 0; index++)
        {
            ItemStack existing = inventory.getItem(index);
            if (existing.isEmpty() || !sameStoredStack(existing, input))
            {
                continue;
            }
            int limit = Math.min(existing.getMaxStackSize(), inventory.getMaxStackSize());
            int move = Math.min(Math.max(0, limit - existing.getCount()), remaining);
            if (move > 0)
            {
                existing.grow(move);
                remaining -= move;
            }
        }
        for (int index = 0; index < PLAYER_ITEM_SLOT_COUNT && remaining > 0; index++)
        {
            if (!inventory.getItem(index).isEmpty())
            {
                continue;
            }
            int move = Math.min(Math.min(input.getMaxStackSize(), inventory.getMaxStackSize()), remaining);
            ItemStack placed = input.copy();
            placed.setCount(move);
            inventory.setItem(index, placed);
            remaining -= move;
        }
        if (remaining != original)
        {
            inventory.setChanged();
        }
        return original - remaining;
    }

    private static void clearSidebarSlots(ServerPlayer player)
    {
        if (player == null || player.containerMenu == null
                || !(player.containerMenu instanceof NetworkStorageMenuAccess access))
        {
            return;
        }
        for (NetworkStorageSlot slot : access.bss$getNetworkSlots())
        {
            slot.clear();
        }
        player.containerMenu.broadcastChanges();
    }

    private static boolean canUseDepositAction(ServerPlayer player, boolean shortcut)
    {
        if (player == null)
        {
            return false;
        }
        if (isSidebarDisabled(player))
        {
            return shortcut;
        }
        return !isSidebarHidden(player);
    }

    private static StorageEntry findEntry(List<StorageEntry> entries, ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return null;
        }
        for (StorageEntry entry : entries)
        {
            if (entry != null && !entry.stack().isEmpty()
                    && sameStoredStack(entry.stack(), stack))
            {
                return entry;
            }
        }
        return null;
    }

    private static ItemStack withCount(ItemStack stack, int count)
    {
        ItemStack copy = stack.copy();
        copy.setCount(Math.max(1, Math.min(count, copy.getMaxStackSize())));
        return copy;
    }

    private static boolean sameStoredStack(ItemStack first, ItemStack second)
    {
        return first != null && second != null && !first.isEmpty() && !second.isEmpty()
                && sameItemAndComponents(first, second);
    }

    private static boolean sameItemAndComponents(ItemStack first, ItemStack second)
    {
        return ItemStack.isSameItemSameComponents(first, second);
    }
}
