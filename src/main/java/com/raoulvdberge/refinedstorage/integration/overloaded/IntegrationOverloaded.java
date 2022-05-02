package com.raoulvdberge.refinedstorage.integration.overloaded;

import net.minecraftforge.fml.common.Loader;

public class IntegrationOverloaded {

    private static final boolean isLoaded;

    static {
        isLoaded = Loader.isModLoaded("overloaded");
    }

    public static boolean isLoaded() {
        return isLoaded;
    }

}
