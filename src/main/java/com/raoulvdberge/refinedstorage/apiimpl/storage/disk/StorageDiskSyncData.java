package com.raoulvdberge.refinedstorage.apiimpl.storage.disk;

import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDiskSyncData;

/**
 * Contains synced info about a storage disk.
 */
public class StorageDiskSyncData implements IStorageDiskSyncData {
    private final long stored;
    private final long capacity;

    public StorageDiskSyncData(long stored, long capacity) {
        this.stored = stored;
        this.capacity = capacity;
    }

    /**
     * @return the amount stored
     */
    public long getStored2() {
        return stored;
    }

    /**
     * @return the capacity
     */
    public long getCapacity2() {
        return capacity;
    }
}
