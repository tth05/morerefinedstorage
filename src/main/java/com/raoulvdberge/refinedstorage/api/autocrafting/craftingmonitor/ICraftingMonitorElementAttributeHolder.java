package com.raoulvdberge.refinedstorage.api.autocrafting.craftingmonitor;

public interface ICraftingMonitorElementAttributeHolder {
    long getScheduled();

    long getProcessing();

    long getCrafting();

    long getStored();
}
