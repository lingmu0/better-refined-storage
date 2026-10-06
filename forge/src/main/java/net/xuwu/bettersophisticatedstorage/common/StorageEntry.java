package net.xuwu.bettersophisticatedstorage.common;

import net.minecraft.world.item.ItemStack;

/** One item key and its amount in the Refined Storage network. */
public record StorageEntry(ItemStack stack, long amount, long insertedTime, long modifiedTime)
{
    public StorageEntry(ItemStack stack, long amount)
    {
        this(stack, amount, 0L, 0L);
    }

    public StorageEntry
    {
        stack = stack == null ? ItemStack.EMPTY : stack.copy();
        if (!stack.isEmpty())
        {
            stack.setCount(1);
        }
        amount = Math.max(0L, amount);
    }
}

