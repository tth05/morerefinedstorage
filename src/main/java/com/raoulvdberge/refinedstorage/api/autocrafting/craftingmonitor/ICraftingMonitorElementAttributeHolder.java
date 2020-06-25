package com.raoulvdberge.refinedstorage.api.autocrafting.craftingmonitor;

public interface ICraftingMonitorElementAttributeHolder {
    int getScheduled();

    int getProcessing();

    int getCrafting();

    int getStored();
}
