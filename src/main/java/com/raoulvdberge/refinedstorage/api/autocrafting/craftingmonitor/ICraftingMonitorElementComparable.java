package com.raoulvdberge.refinedstorage.api.autocrafting.craftingmonitor;

public interface ICraftingMonitorElementComparable {
    long getScheduled();

    long getProcessing();

    long getCrafting();

    long getStored();
}
