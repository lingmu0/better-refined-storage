package net.xuwu.bettersophisticatedstorage.common;

import java.util.List;

/** Common menu access used to append the real network-backed sidebar slots. */
public interface NetworkStorageMenuAccess
{
    List<NetworkStorageSlot> bss$getNetworkSlots();

    void bss$ensureNetworkSlots(int x, int y);

    /**
     * Adds a temporary network slot through AbstractContainerMenu#addSlot so the
     * menu's lastSlots and remoteSlots stay aligned with its public slot list.
     */
    void bss$addNetworkSlot(NetworkStorageSlot slot);

    /** Removes a temporary network slot and its corresponding synchronization state. */
    void bss$removeNetworkSlot(NetworkStorageSlot slot);
}

