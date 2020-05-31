package com.raoulvdberge.refinedstorage.render.model.baked;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import java.util.List;

public class BakedModelDelegate implements IBakedModel {
    protected final IBakedModel base;

    public BakedModelDelegate(IBakedModel base) {
        this.base = base;
    }

    @Nonnull
    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        return base.getQuads(state, side, rand);
    }

    @Override
    public boolean isAmbientOcclusion() {
        return base.isAmbientOcclusion();
    }

    @Override
    public boolean isAmbientOcclusion(@Nonnull IBlockState state) {
        return base.isAmbientOcclusion(state);
    }

    @Override
    public boolean isGui3d() {
        return base.isGui3d();
    }

    @Override
    public boolean isBuiltInRenderer() {
        return base.isBuiltInRenderer();
    }

    @Nonnull
    @Override
    public TextureAtlasSprite getParticleTexture() {
        return base.getParticleTexture();
    }

    @Nonnull
    @Override
    public ItemOverrideList getOverrides() {
        return base.getOverrides();
    }

    @Nonnull
    @Override
    @SuppressWarnings("deprecation")
    public ItemCameraTransforms getItemCameraTransforms() {
        return base.getItemCameraTransforms();
    }

    @Nonnull
    @Override
    public Pair<? extends IBakedModel, Matrix4f> handlePerspective(@Nonnull ItemCameraTransforms.TransformType cameraTransformType) {
        return base.handlePerspective(cameraTransformType);
    }
}
