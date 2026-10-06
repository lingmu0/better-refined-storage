package net.xuwu.bettersophisticatedstorage.client;

import net.xuwu.bettersophisticatedstorage.common.StorageEntry;
import net.xuwu.bettersophisticatedstorage.common.StorageSnapshot;

import java.util.ArrayList;
import java.util.List;

/** Client copy of the last server-authoritative portable-terminal snapshot. */
public final class ClientStorageState
{
    private static volatile StorageSnapshot snapshot = StorageSnapshot.unavailable(false, false, false);
    private static int scrollRow;
    private static long lastAppliedSequence = Long.MIN_VALUE;
    private static long pendingSequence = Long.MIN_VALUE;
    private static int pendingChunkCount;
    private static StorageSnapshot pendingMetadata;
    private static final List<List<StorageEntry>> pendingChunks = new ArrayList<>();

    private ClientStorageState()
    {
    }

    public static synchronized void apply(StorageSnapshot next)
    {
        snapshot = next == null ? StorageSnapshot.unavailable(false, false, false) : next;
        scrollRow = 0;
        lastAppliedSequence = Long.MIN_VALUE;
        clearPending();
    }

    public static synchronized void applySnapshotChunk(long sequence, int chunkIndex, int chunkCount,
                                                        StorageSnapshot chunk)
    {
        if (chunk == null || sequence <= lastAppliedSequence || chunkCount <= 0
                || chunkIndex < 0 || chunkIndex >= chunkCount)
        {
            return;
        }

        if (pendingSequence != sequence || pendingChunkCount != chunkCount)
        {
            clearPending();
            pendingSequence = sequence;
            pendingChunkCount = chunkCount;
            pendingMetadata = chunk;
            for (int index = 0; index < chunkCount; index++)
            {
                pendingChunks.add(null);
            }
        }
        if (pendingChunks.get(chunkIndex) == null)
        {
            pendingChunks.set(chunkIndex, List.copyOf(chunk.entries()));
        }

        for (List<StorageEntry> pendingChunk : pendingChunks)
        {
            if (pendingChunk == null)
            {
                return;
            }
        }

        List<StorageEntry> combined = new ArrayList<>();
        for (List<StorageEntry> pendingChunk : pendingChunks)
        {
            combined.addAll(pendingChunk);
        }
        StorageSnapshot metadata = pendingMetadata;
        snapshot = new StorageSnapshot(metadata.available(), metadata.networkName(),
                metadata.shiftPlayerInventory(), metadata.shiftContainer(), metadata.sidebarHidden(), combined);
        lastAppliedSequence = sequence;
        clearPending();
    }

    public static synchronized void clear()
    {
        snapshot = StorageSnapshot.unavailable(false, false, false);
        scrollRow = 0;
        lastAppliedSequence = Long.MIN_VALUE;
        clearPending();
    }

    public static StorageSnapshot snapshot()
    {
        return snapshot;
    }

    public static boolean available()
    {
        return snapshot.available();
    }

    public static boolean isSidebarHidden()
    {
        return snapshot.sidebarHidden();
    }

    public static synchronized void setSidebarHidden(boolean hidden)
    {
        StorageSnapshot current = snapshot;
        snapshot = new StorageSnapshot(current.available(), current.networkName(),
                current.shiftPlayerInventory(), current.shiftContainer(), hidden, current.entries());
    }

    public static List<StorageEntry> entries()
    {
        return snapshot.entries();
    }

    public static int scrollRow()
    {
        return scrollRow;
    }

    public static void setScrollRow(int row)
    {
        scrollRow = Math.max(0, row);
    }

    public static void resetScroll()
    {
        scrollRow = 0;
    }

    private static void clearPending()
    {
        pendingSequence = Long.MIN_VALUE;
        pendingChunkCount = 0;
        pendingMetadata = null;
        pendingChunks.clear();
    }
}
