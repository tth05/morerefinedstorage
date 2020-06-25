package com.raoulvdberge.refinedstorage.render.model.loader;

import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ICustomModelLoader;
import net.minecraftforge.client.model.IModel;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

public class CustomModelLoaderDefault implements ICustomModelLoader {
    private final ResourceLocation modelLocation;
    private final Supplier<IModel> model;

    public CustomModelLoaderDefault(ResourceLocation modelLocation, Supplier<IModel> model) {
        this.modelLocation = modelLocation;
        this.model = model;
    }

    @Override
    public boolean accepts(@Nonnull ResourceLocation modelLocation) {
        return this.modelLocation.equals(modelLocation);
    }

    @Nonnull
    @Override
    public IModel loadModel(@Nonnull ResourceLocation modelLocation) {
        return model.get();
    }

    @Override
    public void onResourceManagerReload(@Nonnull IResourceManager resourceManager) {
        // NO OP
    }
}
