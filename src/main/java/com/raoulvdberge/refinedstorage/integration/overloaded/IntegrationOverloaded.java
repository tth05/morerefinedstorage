package com.raoulvdberge.refinedstorage.integration.overloaded;

import net.minecraftforge.fml.common.Loader;

public class IntegrationOverloaded {

    public static boolean isLoaded() {
        return Loader.isModLoaded("overloaded");
    }

}
