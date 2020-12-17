package com.raoulvdberge.refinedstorage.tile.config;

import javax.annotation.Nonnull;

public interface IRSTileConfigurationProvider {

    @Nonnull
    RSTileConfiguration getConfig();
}
