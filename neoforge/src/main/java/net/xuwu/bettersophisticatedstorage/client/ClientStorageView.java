package net.xuwu.bettersophisticatedstorage.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.xuwu.bettersophisticatedstorage.common.StorageEntry;
import net.xuwu.bettersophisticatedstorage.common.StorageSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Client-side filtering and deterministic sorting for the compact storage sidebar. */
public final class ClientStorageView
{
    private StorageSnapshot loadedSnapshot;
    private String loadedSearch = "";
    private List<Entry> orderedEntries = List.of();

    public List<Entry> entries(StorageSnapshot snapshot, String searchText)
    {
        String query = searchText == null ? "" : searchText.trim().toLowerCase(Locale.ROOT);
        if (snapshot != loadedSnapshot || !query.equals(loadedSearch))
        {
            loadedSnapshot = snapshot;
            loadedSearch = query;
            ArrayList<Entry> result = new ArrayList<>();
            if (snapshot != null && snapshot.available())
            {
                for (StorageEntry entry : snapshot.entries())
                {
                    if (entry != null && !entry.stack().isEmpty() && entry.amount() > 0L
                            && matches(entry.stack(), query))
                    {
                        result.add(new Entry(entry.stack(), entry.amount()));
                    }
                }
            }
            result.sort(Comparator
                    .comparing((Entry entry) -> entry.key().getHoverName().getString(), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(entry -> registryName(entry.key()), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Entry::amount, Comparator.reverseOrder()));
            orderedEntries = List.copyOf(result);
        }
        return orderedEntries;
    }

    private static boolean matches(ItemStack stack, String query)
    {
        if (query.isEmpty())
        {
            return true;
        }
        return stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)
                || registryName(stack).toLowerCase(Locale.ROOT).contains(query);
    }

    private static String registryName(ItemStack stack)
    {
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    public record Entry(ItemStack key, long amount)
    {
        public Entry
        {
            key = key == null ? ItemStack.EMPTY : key.copy();
            if (!key.isEmpty())
            {
                key.setCount(1);
            }
            amount = Math.max(0L, amount);
        }
    }
}
