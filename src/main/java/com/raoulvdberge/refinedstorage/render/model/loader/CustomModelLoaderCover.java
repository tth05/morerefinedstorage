package com.raoulvdberge.refinedstorage.render.model.loader;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.cover.CoverType;
import com.raoulvdberge.refinedstorage.render.model.ModelCover;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ICustomModelLoader;
import net.minecraftforge.client.model.IModel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CustomModelLoaderCover implements ICustomModelLoader {
    @Override
    public boolean accepts(ResourceLocation modelLocation) {
        return modelLocation.getNamespace().equals(RS.ID) && getType(modelLocation) != null;
    }

    @Nonnull
    @Override
    public IModel loadModel(@Nonnull ResourceLocation modelLocation) {
        return new ModelCover(getType(modelLocation));
    }

    @Nullable
    private CoverType getType(ResourceLocation modelLocation) {
        switch (modelLocation.getPath()) {
            case "cover":
                return CoverType.NORMAL;
            case "hollow_cover":
                return CoverType.HOLLOW;
            default:
                return null;
        }
    }

    @Override
    public void onResourceManagerReload(@Nonnull IResourceManager resourceManager) {
        // NO OP
    }
}
