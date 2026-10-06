package net.xuwu.bettersophisticatedstorage.common;

import net.minecraft.world.entity.player.Player;

import java.util.List;

/** Read-only snapshot adapter for the connected Refined Storage network. */
public final class NetworkStorage
{
    private NetworkStorage()
    {
    }

    public static StorageSnapshot snapshot(Player player)
    {
        boolean shiftPlayer = StorageActions.isShiftPlayerInventoryEnabled(player);
        boolean shiftContainer = StorageActions.isShiftContainerEnabled(player);
        boolean sidebarHidden = StorageActions.isSidebarHidden(player);
        PortableStorageNetwork.Connection connection = PortableStorageNetwork.find(player);
        if (connection == null)
        {
            return StorageSnapshot.unavailable(shiftPlayer, shiftContainer, sidebarHidden);
        }

        return new StorageSnapshot(true, "", shiftPlayer, shiftContainer,
                sidebarHidden, connection.entries());
    }

    public static List<StorageEntry> entries(Player player)
    {
        PortableStorageNetwork.Connection connection = PortableStorageNetwork.find(player);
        return connection == null ? List.of() : connection.entries();
    }
}
