package net.xuwu.bettersophisticatedstorage.common;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** A real menu slot used as the authoritative hit target for one sidebar cell. */
public final class NetworkStorageSlot extends Slot
{
    private static final Container EMPTY_CONTAINER = new SimpleContainer(0);

    private final AbstractContainerMenu owner;
    private final int visualIndex;
    private ItemStack key = ItemStack.EMPTY;
    private long amount;
    private int storageIndex = -1;
    private boolean active;
    private ServerPlayer owningPlayer;

    public NetworkStorageSlot(AbstractContainerMenu owner, int visualIndex, int x, int y)
    {
        super(EMPTY_CONTAINER, 0, x, y);
        this.owner = owner;
        this.visualIndex = visualIndex;
    }

    public void update(int storageIndex, ItemStack key, long amount, boolean active)
    {
        this.storageIndex = -1;
        this.key = ItemStack.EMPTY;
        this.amount = 0L;
        this.active = active;
        if (!active || key == null || key.isEmpty() || amount <= 0L)
        {
            return;
        }

        this.storageIndex = storageIndex;
        this.key = key.copy();
        this.key.setCount(1);
        this.amount = amount;
    }

    public void clear()
    {
        update(-1, ItemStack.EMPTY, 0L, false);
    }

    public void bindPlayer(ServerPlayer player)
    {
        this.owningPlayer = player;
    }

    public int getVisualIndex()
    {
        return visualIndex;
    }

    public int getStorageIndex()
    {
        return storageIndex;
    }

    public ItemStack getKey()
    {
        return key.isEmpty() ? ItemStack.EMPTY : key.copy();
    }

    public long getStoredAmount()
    {
        return amount;
    }

    public ItemStack copyViewStack()
    {
        return getKey();
    }

    @Override
    public ItemStack getItem()
    {
        if (!hasItem())
        {
            return ItemStack.EMPTY;
        }
        ItemStack result = key.copy();
        result.setCount((int) Math.min((long) key.getMaxStackSize(), amount));
        return result;
    }

    @Override
    public boolean hasItem()
    {
        return active && storageIndex >= 0 && amount > 0L && !key.isEmpty();
    }

    @Override
    public boolean mayPlace(ItemStack stack)
    {
        return false;
    }

    @Override
    public boolean mayPickup(Player player)
    {
        return active && hasItem();
    }

    @Override
    public int getMaxStackSize()
    {
        return Integer.MAX_VALUE;
    }

    @Override
    public int getMaxStackSize(ItemStack stack)
    {
        return Integer.MAX_VALUE;
    }

    @Override
    public ItemStack remove(int requestedAmount)
    {
        return owningPlayer == null
                ? ItemStack.EMPTY
                : StorageActions.extractFromSidebar(owningPlayer, this, requestedAmount);
    }

    @Override
    public Optional<ItemStack> tryRemove(int minimumAmount, int maximumAmount, Player player)
    {
        int requested = Math.max(0, Math.min(minimumAmount, maximumAmount));
        if (requested <= 0 || !mayPickup(player))
        {
            return Optional.empty();
        }
        ItemStack extracted = player instanceof ServerPlayer serverPlayer
                ? StorageActions.extractFromSidebar(serverPlayer, this, requested)
                : ItemStack.EMPTY;
        return extracted.isEmpty() ? Optional.empty() : Optional.of(extracted);
    }

    @Override
    public ItemStack safeTake(int minimumAmount, int maximumAmount, Player player)
    {
        Optional<ItemStack> extracted = tryRemove(minimumAmount, maximumAmount, player);
        if (extracted.isPresent())
        {
            onTake(player, extracted.get());
            return extracted.get();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack safeInsert(ItemStack stack)
    {
        return stack;
    }

    @Override
    public ItemStack safeInsert(ItemStack stack, int maximumAmount)
    {
        return stack;
    }

    @Override
    public boolean allowModification(Player player)
    {
        return active && hasItem();
    }

    @Override
    public void set(ItemStack stack)
    {
        // The server-side sidebar click path owns all mutations.
    }

    @Override
    public void setChanged()
    {
        // The storage channel owns change notifications.
    }

    @Override
    public int getContainerSlot()
    {
        return visualIndex;
    }

    @Override
    public boolean isActive()
    {
        return active;
    }

    @Override
    public boolean isSameInventory(Slot other)
    {
        return other instanceof NetworkStorageSlot networkSlot && networkSlot.owner == owner;
    }

    void reduceAmount(int extracted)
    {
        amount = Math.max(0L, amount - Math.max(0, extracted));
        if (amount == 0L)
        {
            clear();
        }
    }
}
