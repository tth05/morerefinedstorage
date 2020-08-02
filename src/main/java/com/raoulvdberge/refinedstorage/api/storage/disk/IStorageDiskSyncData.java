package com.raoulvdberge.refinedstorage.api.storage.disk;

public interface IStorageDiskSyncData {

    long getCapacity2();

    /**
     * Reborn storage...
     * @deprecated use {@link #getCapacity2()}
     */
    @Deprecated
    default int getCapacity() {
        return (int) getCapacity2();
    }

    long getStored2();

    /**
     * Reborn storage...
     * @deprecated use {@link #getStored2()}
     */
    default int getStored() {
        return (int) getStored2();
    }
}
